package com.sportacus.backend.profile;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.analytics.AnalyticsService;
import com.sportacus.backend.common.Answers;
import com.sportacus.backend.health.HealthService;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.legal.ConsentLogRepository;
import com.sportacus.backend.profile.ProfileAnswerRepository.State;
import com.sportacus.backend.profile.bank.BankModels.Area;
import com.sportacus.backend.profile.bank.BankModels.BankQuestion;
import com.sportacus.backend.profile.bank.BankModels.Questionnaire;
import com.sportacus.backend.profile.bank.QuestionBank;
import com.sportacus.backend.world.catalog.CatalogRepository;
import com.sportacus.backend.world.core.ProfileFacts;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.core.ProfileInsights.Readiness;
import com.sportacus.backend.world.core.UserWorldData;
import com.sportacus.backend.world.core.WorldRegistry;
import com.sportacus.backend.world.enrollment.UserWorldRepository;
import com.sportacus.backend.world.form.FormSpec;
import com.sportacus.backend.world.metrics.MetricRepository;

/**
 * Perfil 360 (docs/19): áreas, cuestionarios, respuestas sueltas, consentimiento, check-in diario y el motor de
 * "siguiente mejor pregunta". Principio de no presión: nada es obligatorio, todo se puede omitir, posponer o
 * responder "prefiero no decir", y el motor sugiere pocas preguntas por día.
 */
@Service
public class Profile360Service {

    public static final String HEALTH_AREA = "salud";
    public static final String CHECKIN_QUESTIONNAIRE = "checkin_diario";
    /** Peso del chequeo de salud en el % del perfil (equivale a este número de preguntas). */
    private static final int HEALTH_WEIGHT = 11;
    private static final int DAILY_SUGGESTION_CAP = 6;

    private final QuestionBank bank;
    private final ProfileAnswerRepository answers;
    private final ProfileRepository profiles;
    private final ProfileFactsService factsService;
    private final HealthService health;
    private final MetricRepository metrics;
    private final UserWorldRepository userWorlds;
    private final WorldRegistry registry;
    private final CatalogRepository catalog;
    private final JdbcTemplate jdbc;
    private final AnalyticsService analytics;
    private final ConsentLogRepository consentLog;

    public Profile360Service(QuestionBank bank, ProfileAnswerRepository answers, ProfileRepository profiles,
            ProfileFactsService factsService, HealthService health, MetricRepository metrics,
            UserWorldRepository userWorlds, WorldRegistry registry, CatalogRepository catalog, JdbcTemplate jdbc,
            AnalyticsService analytics, ConsentLogRepository consentLog) {
        this.bank = bank;
        this.answers = answers;
        this.profiles = profiles;
        this.factsService = factsService;
        this.health = health;
        this.metrics = metrics;
        this.userWorlds = userWorlds;
        this.registry = registry;
        this.catalog = catalog;
        this.jdbc = jdbc;
        this.analytics = analytics;
        this.consentLog = consentLog;
    }

    // ── Vistas ────────────────────────────────────────────────

    public record Completeness(int pct, String level, String label) {
    }

    public record QuestionnaireSummary(String key, String title, String subtitle, Integer minutes, int answered,
            int total, int pct) {
    }

    public record AreaView(String key, String name, String icon, String description, boolean sensitive,
            boolean consented, int pct, int answered, int total, String handler,
            List<QuestionnaireSummary> questionnaires) {
    }

    public record Overview(Completeness completeness, List<AreaView> areas) {
    }

    public record QuestionMeta(String why, List<String> uses, String status, OffsetDateTime answeredAt) {
    }

    public record QuestionnaireView(String key, String areaKey, String areaName, boolean sensitive, boolean consented,
            FormSpec form, Map<String, QuestionMeta> meta) {
    }

    /**
     * Sugerencia del motor. {@code id} es lo que se envía a /profile/answers/{id}/skip: el id de la pregunta o
     * "health", "consent:&lt;área&gt;", "refine:&lt;mundo&gt;".
     */
    public record NextItem(String id, String kind, String areaKey, String areaName, String areaIcon,
            String questionnaireKey, FormSpec.Question question, String why, String title, String body, String route) {
    }

    public record SaveResult(int saved, Completeness completeness, List<String> highlights, List<NextItem> next) {
    }

    public record CheckinView(boolean done, FormSpec form, Readiness readiness) {
    }

    public record CheckinResult(Readiness readiness, List<String> highlights) {
    }

    /** Estado de cálculo compartido por las vistas de una petición. */
    private record Snapshot(ProfileFacts facts, Map<String, State> states, Map<String, Boolean> consents,
            HealthSummary health) {

        boolean consented(String areaKey, boolean sensitive) {
            return !sensitive || Boolean.TRUE.equals(consents.get(areaKey));
        }

        boolean noDecir(String q) {
            State s = states.get(q);
            return s != null && "no_decir".equals(s.status());
        }
    }

    private Snapshot snapshot(UUID userId) {
        return new Snapshot(factsService.facts(userId), answers.states(userId), answers.consents(userId),
                health.summary(userId));
    }

    public Overview overview(UUID userId) {
        Snapshot s = snapshot(userId);
        List<AreaView> areas = new ArrayList<>();
        areas.add(healthArea(s));
        for (Area a : bank.areas()) {
            if (isCheckin(a.key())) continue;
            areas.add(areaView(a, s));
        }
        return new Overview(completeness(s), areas);
    }

    public Completeness completeness(UUID userId) {
        return completeness(snapshot(userId));
    }

    private Completeness completeness(Snapshot s) {
        int answered = s.health().screened() ? HEALTH_WEIGHT : 0;
        int total = HEALTH_WEIGHT;
        for (Area a : bank.areas()) {
            if (isCheckin(a.key())) continue;
            int[] c = count(a, s);
            answered += c[0];
            total += c[1];
        }
        int pct = total == 0 ? 0 : (int) Math.round(100.0 * answered / total);
        String level = pct < 15 ? "inicial" : pct < 40 ? "basico" : pct < 75 ? "bueno" : "completo";
        String label = switch (level) {
            case "inicial" -> "Te estamos conociendo";
            case "basico" -> "Ya sabemos lo básico de ti";
            case "bueno" -> "Te conocemos bien";
            default -> "Perfil muy completo: recomendaciones a tu medida";
        };
        return new Completeness(pct, level, label);
    }

    private AreaView healthArea(Snapshot s) {
        int pct = s.health().screened() ? 100 : 0;
        return new AreaView(HEALTH_AREA, "Salud y seguridad", "🩺",
                "Chequeo rápido para cuidarte: condiciones, síntomas, lesiones y medicamentos. Se guarda cifrado.",
                true, s.consented(HEALTH_AREA, true), pct, s.health().screened() ? 1 : 0, 1, "health",
                List.of(new QuestionnaireSummary("salud_chequeo", "Chequeo de salud", "1 minuto", 1,
                        s.health().screened() ? 1 : 0, 1, pct)));
    }

    private AreaView areaView(Area a, Snapshot s) {
        List<QuestionnaireSummary> qs = new ArrayList<>();
        int answered = 0;
        int total = 0;
        for (Questionnaire q : bank.questionnaires(a.key())) {
            int[] c = count(q, s);
            answered += c[0];
            total += c[1];
            qs.add(new QuestionnaireSummary(q.key(), q.title(), q.subtitle(), q.minutes(), c[0], c[1], pct(c)));
        }
        return new AreaView(a.key(), a.name(), a.icon(), a.description(), a.sensitive(),
                s.consented(a.key(), a.sensitive()), pct(new int[] { answered, total }), answered, total, null, qs);
    }

    private int[] count(Area a, Snapshot s) {
        int answered = 0;
        int total = 0;
        for (Questionnaire q : bank.questionnaires(a.key())) {
            int[] c = count(q, s);
            answered += c[0];
            total += c[1];
        }
        return new int[] { answered, total };
    }

    /** [respondidas, visibles] sin contar el check-in diario. "Prefiero no decir" cuenta como respondida. */
    private int[] count(Questionnaire q, Snapshot s) {
        int answered = 0;
        int total = 0;
        for (BankQuestion bq : q.questions()) {
            if (bq.daily() || !visible(bq, s.facts())) continue;
            total++;
            if (s.facts().has(bq.id()) || s.noDecir(bq.id())) answered++;
        }
        return new int[] { answered, total };
    }

    private static int pct(int[] c) {
        return c[1] == 0 ? 0 : (int) Math.round(100.0 * c[0] / c[1]);
    }

    public QuestionnaireView questionnaire(UUID userId, String key) {
        Questionnaire q = requireQuestionnaire(key);
        String areaKey = bank.areaOf(q.questions().isEmpty() ? null : q.questions().get(0).id());
        Area area = bank.area(areaKey).orElseThrow();
        Snapshot s = snapshot(userId);
        boolean consented = s.consented(area.key(), area.sensitive());
        Map<String, Object> prefill = new LinkedHashMap<>();
        Map<String, QuestionMeta> meta = new LinkedHashMap<>();
        List<FormSpec.Question> questions = new ArrayList<>();
        Map<String, ProfileAnswerRepository.Latest> latest = consented ? answers.latest(userId) : Map.of();
        for (BankQuestion bq : q.questions()) {
            questions.add(bq.toForm());
            if (consented && s.facts().has(bq.id())) prefill.put(bq.id(), s.facts().answers().values().get(bq.id()));
            State st = s.states().get(bq.id());
            var l = latest.get(bq.id());
            meta.put(bq.id(), new QuestionMeta(bq.why(), bq.uses() == null ? List.of() : bq.uses(),
                    st == null ? null : st.status(), l == null ? null : l.answeredAt()));
        }
        FormSpec form = new FormSpec(q.key(), q.title(), q.subtitle(), questions, Map.of()).withAnswers(prefill);
        return new QuestionnaireView(q.key(), area.key(), area.name(), area.sensitive(), consented, form, meta);
    }

    // ── Guardado ──────────────────────────────────────────────

    @Transactional
    public SaveResult saveQuestionnaire(UUID userId, String key, Map<String, Object> body) {
        Questionnaire q = requireQuestionnaire(key);
        Set<String> ids = new HashSet<>();
        q.questions().forEach(bq -> ids.add(bq.id()));
        int saved = 0;
        List<String> highlights = new ArrayList<>();
        int before = completeness(userId).pct();
        for (Map.Entry<String, Object> e : body.entrySet()) {
            if (!ids.contains(e.getKey())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La pregunta " + e.getKey() + " no pertenece a este cuestionario");
            }
            if (record(userId, e.getKey(), e.getValue(), "cuestionario", highlights)) saved++;
        }
        return result(userId, saved, before, highlights, "home");
    }

    @Transactional
    public SaveResult answer(UUID userId, String questionId, Object value) {
        int before = completeness(userId).pct();
        List<String> highlights = new ArrayList<>();
        boolean ok = record(userId, questionId, value, "sugerida", highlights);
        return result(userId, ok ? 1 : 0, before, highlights, "home");
    }

    private SaveResult result(UUID userId, int saved, int before, List<String> highlights, String context) {
        Completeness after = completeness(userId);
        if (after.pct() > before) highlights.add(0, "Tu perfil está al " + after.pct() + " %");
        return new SaveResult(saved, after, highlights, next(userId, 2, context));
    }

    /**
     * Valida y guarda una respuesta: historial (cifrado si el área es sensible), columna del perfil común, cuenta o
     * métrica según la pregunta. Valores vacíos se ignoran (omitir es válido).
     */
    private boolean record(UUID userId, String questionId, Object value, String source, List<String> highlights) {
        BankQuestion bq = bank.question(questionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pregunta desconocida: " + questionId));
        if (isEmpty(value)) return false;
        String areaKey = bank.areaOf(questionId);
        boolean sensitive = bank.isSensitive(areaKey);
        if (sensitive && !Boolean.TRUE.equals(answers.consents(userId).get(areaKey))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Para guardar datos de esta área necesitamos primero tu consentimiento");
        }
        boolean checkin = isCheckin(areaKey);
        if (checkin) health.requireConsent(userId); // el check-in diario es dato de salud
        Object clean = validate(bq, value);
        if ("p_birth_year".equals(questionId)) {
            int age = java.time.Year.now().getValue() - (int) Math.round(Double.parseDouble(String.valueOf(clean)));
            if (age < 12) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sportacus es para personas de 12 años o más");
            }
        }
        if (checkin) replaceTodayCheckin(userId, bq);
        answers.insert(userId, questionId, areaKey, clean, sensitive, source);
        if (!checkin) analytics.track(userId, "profile_question_answered", Map.of("area", areaKey));
        Answers single = new Answers(Map.of(questionId, clean));
        if (bq.profileField() != null) {
            switch (bq.profileField()) {
                case "country" -> jdbc.update("UPDATE app_user SET country = ? WHERE id = ?", trim(clean, 60), userId);
                case "city" -> jdbc.update("UPDATE app_user SET city = ? WHERE id = ?", trim(clean, 80), userId);
                default -> profiles.patchField(userId, bq.profileField(), single, questionId);
            }
        }
        if (bq.metric() != null) {
            Double n = single.number(questionId);
            if (n != null) {
                metrics.insert(userId, null, bq.metric(), n, "manual", questionId, Map.of("pregunta", questionId));
            }
        }
        return true;
    }

    /**
     * Check-in: una entrada por día. Si la persona lo repite hoy, la respuesta y la métrica de hoy de esa pregunta se
     * reemplazan en vez de duplicarse (las de días anteriores quedan intactas).
     */
    private void replaceTodayCheckin(UUID userId, BankQuestion bq) {
        jdbc.update("""
                DELETE FROM profile_answer WHERE user_id = ? AND question_key = ? AND area_key = 'checkin'
                  AND (answered_at AT TIME ZONE 'America/Bogota')::date = (now() AT TIME ZONE 'America/Bogota')::date
                """, userId, bq.id());
        if (bq.metric() != null) {
            jdbc.update("""
                    DELETE FROM metric_entry WHERE user_id = ? AND metric_key = ? AND source_ref = ?
                      AND (recorded_at AT TIME ZONE 'America/Bogota')::date = (now() AT TIME ZONE 'America/Bogota')::date
                    """, userId, bq.metric(), bq.id());
        }
    }

    /** Registra en el historial lo guardado desde el formulario clásico del perfil (pantalla de cuenta). */
    public void recordProfileForm(UUID userId, Answers a) {
        for (String id : ProfileRepository.FIELDS.keySet()) {
            if (!a.values().containsKey(id) || isEmpty(a.values().get(id))) continue;
            var bq = bank.question(id);
            if (bq.isEmpty()) continue;
            answers.insert(userId, id, bank.areaOf(id), a.values().get(id), false, "perfil");
            if (bq.get().metric() != null && a.number(id) != null) {
                metrics.insert(userId, null, bq.get().metric(), a.number(id), "manual", id, Map.of("pregunta", id));
            }
        }
    }

    private static String trim(Object v, int max) {
        String s = String.valueOf(v).trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static boolean isEmpty(Object v) {
        if (v == null) return true;
        if (v instanceof String s) return s.isBlank();
        if (v instanceof java.util.Collection<?> c) return c.isEmpty();
        return false;
    }

    /** Normaliza y valida contra la definición; lanza 400 con un mensaje claro si no encaja. */
    static Object validate(BankQuestion q, Object value) {
        Answers a = new Answers(Map.of(q.id(), value));
        switch (q.type()) {
            case "options" -> {
                String v = a.string(q.id());
                if (!q.hasOption(v)) throw bad(q, "opción no válida");
                return v;
            }
            case "multi" -> {
                List<String> vs = a.list(q.id());
                for (String v : vs) if (!q.hasOption(v)) throw bad(q, "opción no válida: " + v);
                if (q.maxChoices() != null && vs.size() > q.maxChoices()) throw bad(q, "demasiadas opciones");
                return vs;
            }
            case "range", "number" -> {
                Double n = a.number(q.id());
                if (n == null) throw bad(q, "se esperaba un número");
                if (n < q.min() || n > q.max()) throw bad(q, "fuera de rango (" + q.min() + "–" + q.max() + ")");
                return "range".equals(q.type()) ? (Object) (int) Math.round(n) : (Object) n;
            }
            case "date" -> {
                String v = a.string(q.id());
                try {
                    return LocalDate.parse(v).toString();
                } catch (DateTimeParseException e) {
                    throw bad(q, "fecha inválida (AAAA-MM-DD)");
                }
            }
            default -> {
                String v = a.string(q.id());
                return v.length() > 200 ? v.substring(0, 200) : v;
            }
        }
    }

    private static ResponseStatusException bad(BankQuestion q, String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "\"" + q.prompt() + "\": " + msg);
    }

    // ── Omitir y consentimiento ───────────────────────────────

    @Transactional
    public List<NextItem> skip(UUID userId, String id, String mode) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean special = !bank.question(id).isPresent();
        if (special && !(id.equals("health") || id.startsWith("consent:") || id.startsWith("refine:"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pregunta desconocida: " + id);
        }
        String m = mode == null ? "omitir" : mode;
        switch (m) {
            case "posponer" -> answers.setState(userId, id, "pospuesta", now.plusDays(3));
            case "no_decir" -> {
                if (special) answers.setState(userId, id, "omitida", now.plusDays(30));
                else answers.setState(userId, id, "no_decir", null);
            }
            case "omitir" -> answers.setState(userId, id, "omitida", now.plusDays(30));
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modo inválido: " + m);
        }
        analytics.track(userId, "profile_question_skipped", Map.of("mode", m));
        return next(userId, 2, "home");
    }

    /**
     * Consentimiento por área sensible. El área "salud" (chequeo de salud y check-in diario) no está en el banco de
     * preguntas: sin ella no se guardan chequeos ni check-ins, y revocarla borra esos datos. Cada cambio queda en
     * {@code consent_log}.
     */
    @Transactional
    public AreaView consent(UUID userId, String areaKey, boolean granted) {
        if (HEALTH_AREA.equals(areaKey)) {
            answers.setConsent(userId, HEALTH_AREA, granted);
            consentLog.record(userId, ConsentLogRepository.HEALTH_DATA, null, granted, "app");
            if (!granted) {
                List<String> ids = new ArrayList<>();
                bank.questionnaires("checkin").forEach(q -> q.questions().forEach(bq -> ids.add(bq.id())));
                health.deleteHealthData(userId, ids);
            }
            analytics.track(userId, "consent_changed", Map.of("area", HEALTH_AREA, "granted", granted));
            return healthArea(snapshot(userId));
        }
        Area a = bank.area(areaKey).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Área desconocida"));
        if (!a.sensitive()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta área no requiere consentimiento");
        answers.setConsent(userId, areaKey, granted);
        consentLog.record(userId, areaKey, null, granted, "app");
        analytics.track(userId, "consent_changed", Map.of("area", areaKey, "granted", granted));
        if (!granted) {
            List<String> ids = new ArrayList<>();
            bank.questionnaires(areaKey).forEach(q -> q.questions().forEach(bq -> ids.add(bq.id())));
            answers.deleteArea(userId, areaKey, ids);
        }
        return areaView(a, snapshot(userId));
    }

    // ── Check-in diario ───────────────────────────────────────

    public CheckinView checkin(UUID userId, HealthSummary h) {
        Questionnaire q = requireQuestionnaire(CHECKIN_QUESTIONNAIRE);
        ProfileFacts f = factsService.facts(userId);
        LocalDate today = LocalDate.now(MetricRepository.ZONE);
        boolean done = q.questions().stream().anyMatch(bq -> f.answeredOn(bq.id(), today));
        Map<String, Object> prefill = new LinkedHashMap<>();
        if (done) {
            for (BankQuestion bq : q.questions()) {
                if (f.answeredOn(bq.id(), today)) prefill.put(bq.id(), f.answers().values().get(bq.id()));
            }
        }
        FormSpec form = new FormSpec(q.key(), q.title(), q.subtitle(),
                q.questions().stream().map(BankQuestion::toForm).toList(), Map.of()).withAnswers(prefill);
        Readiness r = done ? InsightRules.derive(f, h, today).readiness() : null;
        return new CheckinView(done, form, r);
    }

    @Transactional
    public CheckinResult saveCheckin(UUID userId, Map<String, Object> body, HealthSummary h) {
        health.requireConsent(userId);
        Questionnaire q = requireQuestionnaire(CHECKIN_QUESTIONNAIRE);
        Set<String> ids = new HashSet<>();
        q.questions().forEach(bq -> ids.add(bq.id()));
        List<String> highlights = new ArrayList<>();
        int saved = 0;
        for (Map.Entry<String, Object> e : body.entrySet()) {
            if (!ids.contains(e.getKey())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pregunta ajena al check-in: " + e.getKey());
            }
            if (record(userId, e.getKey(), e.getValue(), "checkin", highlights)) saved++;
        }
        if (saved > 0) analytics.track(userId, "checkin_saved", Map.of("answered", saved));
        ProfileInsights insights = factsService.insights(userId, h);
        Readiness r = insights.readiness();
        if (r != null) highlights.add(r.label());
        return new CheckinResult(r, highlights);
    }

    // ── Motor de siguiente mejor pregunta ─────────────────────

    /**
     * Sugerencias ordenadas por valor: chequeo de salud pendiente, preguntas sueltas (prioridad, área poco completa,
     * relación con el mundo activo y el momento), consentimientos de áreas sensibles y afinar el plan del mundo. Respeta
     * omitidas/pospuestas/"prefiero no decir" y un tope diario de respuestas sugeridas.
     *
     * @param context home | post_session | world
     */
    public List<NextItem> next(UUID userId, int limit, String context) {
        Snapshot s = snapshot(userId);
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate today = LocalDate.now(MetricRepository.ZONE);
        List<Scored> scored = new ArrayList<>();
        UserWorldData active = userWorlds.findActive(userId).orElse(null);
        String world = active == null ? null : active.worldKey();

        if (!s.health().screened() && !blocked(s, "health", now)) {
            scored.add(new Scored(100, new NextItem("health", "health", HEALTH_AREA, "Salud y seguridad", "🩺", null,
                    null, null, "Tu chequeo de salud (1 minuto)",
                    "Desbloquea la intensidad alta y nos permite cuidar tus lesiones y condiciones.", "/health")));
        }
        boolean capped = answers.suggestedToday(userId) >= DAILY_SUGGESTION_CAP;
        if (!capped) {
            Map<String, Integer> areaPct = new LinkedHashMap<>();
            for (Area a : bank.areas()) areaPct.put(a.key(), pct(count(a, s)));
            for (Area a : bank.areas()) {
                if (isCheckin(a.key())) continue;
                if (a.sensitive() && !s.consented(a.key(), true)) {
                    String id = "consent:" + a.key();
                    if (!s.consents().containsKey(a.key()) && !blocked(s, id, now)) {
                        int maxP = bank.questionnaires(a.key()).stream().flatMap(q -> q.questions().stream())
                                .mapToInt(BankQuestion::priorityOr3).max().orElse(3);
                        String first = bank.questionnaires(a.key()).isEmpty() ? null
                                : bank.questionnaires(a.key()).get(0).key();
                        scored.add(new Scored(20 + maxP * 5, new NextItem(id, "consent", a.key(), a.name(), a.icon(),
                                first, null, null, a.name() + ": privado y opcional",
                                a.description() + " Se guarda cifrado y puedes borrarlo cuando quieras.",
                                first == null ? "/profile360" : "/profile360/" + first)));
                    }
                    continue;
                }
                for (Questionnaire q : bank.questionnaires(a.key())) {
                    int index = 0;
                    for (BankQuestion bq : q.questions()) {
                        index++;
                        if (bq.daily() || !visible(bq, s.facts()) || blocked(s, bq.id(), now)) continue;
                        if (s.facts().has(bq.id()) && !due(bq, s.facts(), today)) continue;
                        double score = bq.priorityOr3() * 10 + (100 - areaPct.getOrDefault(a.key(), 0)) / 10.0
                                - index * 0.2;
                        List<String> uses = bq.uses() == null ? List.of() : bq.uses();
                        if (uses.contains("seguridad")) score += 8;
                        if (uses.contains("plan")) score += 4;
                        if (world != null && uses.contains(world)) score += 12;
                        if ("post_session".equals(context)
                                && (a.key().equals("sueno") || a.key().equals("bienestar") || a.key().equals("habitos"))) {
                            score += 15;
                        }
                        if (s.facts().has(bq.id())) score -= 5; // repreguntar pesa menos que preguntar algo nuevo
                        scored.add(new Scored(score, new NextItem(bq.id(), "question", a.key(), a.name(), a.icon(),
                                q.key(), bq.toForm(), bq.why(), null, null, null)));
                    }
                }
            }
        }
        if (active != null && !"base".equals(world) && registry.isImplemented(world)) {
            String id = "refine:" + world;
            int pending = pendingWorldQuestions(userId, active);
            if (pending > 0 && !blocked(s, id, now)) {
                String name = catalog.world(world).map(w -> w.name()).orElse(world);
                scored.add(new Scored(45, new NextItem(id, "refine_world", null, name, null, null, null, null,
                        "Afina tu plan de " + name,
                        pending == 1 ? "1 pregunta opcional hace tu plan más preciso."
                                : pending + " preguntas opcionales hacen tu plan más preciso.",
                        "/onboarding/setup/" + world + "?mode=edit")));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<NextItem> out = new ArrayList<>();
        Set<String> usedQuestionnaires = new HashSet<>();
        for (Scored sc : scored) {
            if (out.size() >= limit) break;
            String qk = sc.item().questionnaireKey();
            if ("question".equals(sc.item().kind()) && qk != null && !usedQuestionnaires.add(qk)) continue;
            out.add(sc.item());
        }
        return out;
    }

    private record Scored(double score, NextItem item) {
    }

    private int pendingWorldQuestions(UUID userId, UserWorldData uw) {
        try {
            var form = registry.require(uw.worldKey()).onboardingForm(
                    new com.sportacus.backend.world.core.UserContext(userId, "", profiles.load(userId),
                            health.summary(userId)));
            int n = 0;
            for (FormSpec.Question q : form.questions()) {
                if (!q.required() && q.showIf() == null && !uw.profile().has(q.id())) n++;
            }
            return n;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static boolean blocked(Snapshot s, String id, OffsetDateTime now) {
        State st = s.states().get(id);
        if (st == null) return false;
        return switch (st.status()) {
            case "no_decir" -> true;
            case "omitida", "pospuesta" -> st.until() != null && st.until().isAfter(now);
            default -> false;
        };
    }

    /** ¿Toca repreguntar? (respuesta más vieja que refreshDays). */
    private static boolean due(BankQuestion q, ProfileFacts f, LocalDate today) {
        if (q.refreshDays() == null) return false;
        LocalDate at = f.answeredOn().get(q.id());
        return at != null && !at.plusDays(q.refreshDays()).isAfter(today);
    }

    /** showIf: visible si la respuesta de la pregunta objetivo contiene alguno de los valores. */
    static boolean visible(BankQuestion q, ProfileFacts f) {
        if (q.showIf() == null) return true;
        Object target = q.showIf().get("question");
        Object in = q.showIf().get("in");
        if (!(target instanceof String t) || !(in instanceof List<?> values)) return true;
        for (String v : f.list(t)) {
            if (values.stream().anyMatch(x -> String.valueOf(x).equals(v))) return true;
        }
        return false;
    }

    private Questionnaire requireQuestionnaire(String key) {
        return bank.questionnaire(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuestionario no encontrado"));
    }

    private static boolean isCheckin(String areaKey) {
        return "checkin".equals(areaKey);
    }
}
