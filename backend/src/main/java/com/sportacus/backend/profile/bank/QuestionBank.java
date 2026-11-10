package com.sportacus.backend.profile.bank;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.sportacus.backend.common.Json;
import com.sportacus.backend.content.ContentModels.Metric;
import com.sportacus.backend.profile.bank.BankModels.Area;
import com.sportacus.backend.profile.bank.BankModels.AreaFile;
import com.sportacus.backend.profile.bank.BankModels.BankQuestion;
import com.sportacus.backend.profile.bank.BankModels.Questionnaire;
import com.sportacus.backend.world.catalog.CatalogRepository;

/**
 * Banco de preguntas del Perfil 360, cargado en memoria desde {@code content/questionnaires/*.json} al arrancar
 * (después del ContentLoader). Igual que el resto del contenido, un error de formato detiene el arranque.
 */
@Component
@Order(20)
public class QuestionBank implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QuestionBank.class);
    static final Set<String> TYPES = Set.of("options", "multi", "range", "number", "text", "date");
    static final Set<String> PROFILE_FIELDS = Set.of("birth_year", "sex", "weight_kg", "height_cm", "activity_level",
            "days_per_week", "minutes_per_session", "preferred_days", "environments", "equipment", "country", "city");

    private final Json json;
    private final JdbcTemplate jdbc;
    private final CatalogRepository catalog;

    private volatile List<Area> areas = List.of();
    private volatile Map<String, Questionnaire> questionnaires = Map.of();
    private volatile Map<String, List<Questionnaire>> byArea = Map.of();
    private volatile Map<String, BankQuestion> questions = Map.of();
    private volatile Map<String, String> areaOfQuestion = Map.of();
    private volatile Map<String, String> questionnaireOfQuestion = Map.of();

    public QuestionBank(Json json, JdbcTemplate jdbc, CatalogRepository catalog) {
        this.json = json;
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:content/questionnaires/*.json");
        List<AreaFile> parsed = new ArrayList<>();
        for (Resource r : files) {
            try {
                parsed.add(json.read(new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8),
                        AreaFile.class));
            } catch (RuntimeException e) {
                throw new IllegalStateException("Cuestionario inválido en " + r.getFilename() + ": " + e.getMessage(), e);
            }
        }
        load(parsed);
        List<Metric> metrics = new ArrayList<>();
        parsed.forEach(f -> metrics.addAll(f.metrics() == null ? List.of() : f.metrics()));
        for (Metric m : metrics) {
            jdbc.update("""
                    INSERT INTO metric_definition (key, world_key, name, unit, value_type, direction, description)
                    VALUES (?, NULL, ?, ?, ?, ?, ?)
                    ON CONFLICT (key) DO UPDATE SET name = EXCLUDED.name, unit = EXCLUDED.unit,
                        value_type = EXCLUDED.value_type, direction = EXCLUDED.direction, description = EXCLUDED.description
                    """, m.key(), m.name(), m.unit(), m.valueType() == null ? "number" : m.valueType(),
                    m.direction() == null ? "up" : m.direction(), m.description());
        }
        if (!metrics.isEmpty()) catalog.invalidate();
        Set<String> knownMetrics = catalog.metrics().keySet();
        for (BankQuestion q : questions.values()) {
            if (q.metric() != null && !knownMetrics.contains(q.metric())) {
                throw new IllegalStateException("La pregunta " + q.id() + " usa la métrica no declarada " + q.metric());
            }
        }
        log.info("Perfil 360: {} áreas, {} cuestionarios, {} preguntas", areas.size(), questionnaires.size(),
                questions.size());
    }

    /** Valida e indexa (separado de {@link #run} para poder probarlo sin Spring). */
    void load(List<AreaFile> files) {
        List<Area> as = new ArrayList<>();
        Map<String, Questionnaire> qs = new LinkedHashMap<>();
        Map<String, List<Questionnaire>> ba = new LinkedHashMap<>();
        Map<String, BankQuestion> qq = new LinkedHashMap<>();
        Map<String, String> aoq = new LinkedHashMap<>();
        Map<String, String> qoq = new LinkedHashMap<>();
        Set<String> areaKeys = new HashSet<>();
        for (AreaFile f : files) {
            Area a = f.area();
            if (a == null || a.key() == null || a.name() == null) throw new IllegalStateException("Área sin key o name");
            if (!areaKeys.add(a.key())) throw new IllegalStateException("Área repetida: " + a.key());
            as.add(a);
            List<Questionnaire> list = new ArrayList<>();
            for (Questionnaire q : f.questionnaires() == null ? List.<Questionnaire>of() : f.questionnaires()) {
                if (qs.put(q.key(), q) != null) throw new IllegalStateException("Cuestionario repetido: " + q.key());
                list.add(q);
                for (BankQuestion bq : q.questions() == null ? List.<BankQuestion>of() : q.questions()) {
                    validate(bq);
                    if (qq.put(bq.id(), bq) != null) throw new IllegalStateException("Pregunta repetida: " + bq.id());
                    aoq.put(bq.id(), a.key());
                    qoq.put(bq.id(), q.key());
                }
            }
            ba.put(a.key(), list);
        }
        for (BankQuestion bq : qq.values()) {
            if (bq.showIf() == null) continue;
            Object target = bq.showIf().get("question");
            if (!(target instanceof String t) || !(qq.containsKey(t) || t.startsWith("s_"))) {
                throw new IllegalStateException("showIf de " + bq.id() + " apunta a una pregunta inexistente: " + target);
            }
        }
        as.sort(Comparator.comparingInt(a -> a.sortOrder() == null ? 999 : a.sortOrder()));
        this.areas = List.copyOf(as);
        this.questionnaires = qs;
        this.byArea = ba;
        this.questions = qq;
        this.areaOfQuestion = aoq;
        this.questionnaireOfQuestion = qoq;
    }

    private static void validate(BankQuestion q) {
        if (q.id() == null || q.id().length() > 60) throw new IllegalStateException("Pregunta sin id o id > 60");
        if (!TYPES.contains(q.type())) throw new IllegalStateException("Tipo inválido en " + q.id() + ": " + q.type());
        if (q.prompt() == null) throw new IllegalStateException("Pregunta sin texto: " + q.id());
        boolean choice = q.type().equals("options") || q.type().equals("multi");
        if (choice && (q.options() == null || q.options().isEmpty())) {
            throw new IllegalStateException("Pregunta de opciones sin opciones: " + q.id());
        }
        if ((q.type().equals("range") || q.type().equals("number")) && (q.min() == null || q.max() == null
                || q.min() >= q.max())) {
            throw new IllegalStateException("Rango inválido en " + q.id());
        }
        if (q.profileField() != null && !PROFILE_FIELDS.contains(q.profileField())) {
            throw new IllegalStateException("profileField desconocido en " + q.id() + ": " + q.profileField());
        }
    }

    // ── Consultas ─────────────────────────────────────────────

    public List<Area> areas() {
        return areas;
    }

    public Optional<Area> area(String key) {
        return areas.stream().filter(a -> a.key().equals(key)).findFirst();
    }

    public List<Questionnaire> questionnaires(String areaKey) {
        return byArea.getOrDefault(areaKey, List.of());
    }

    public Optional<Questionnaire> questionnaire(String key) {
        return Optional.ofNullable(questionnaires.get(key));
    }

    public Optional<BankQuestion> question(String id) {
        return Optional.ofNullable(questions.get(id));
    }

    public java.util.Collection<BankQuestion> allQuestions() {
        return questions.values();
    }

    public String areaOf(String questionId) {
        return areaOfQuestion.get(questionId);
    }

    public String questionnaireOf(String questionId) {
        return questionnaireOfQuestion.get(questionId);
    }

    public boolean isSensitive(String areaKey) {
        return area(areaKey).map(Area::sensitive).orElse(false);
    }
}
