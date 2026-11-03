package com.sportacus.backend.questionnaire;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.sportacus.backend.catalog.dto.EquipmentResponse;
import com.sportacus.backend.questionnaire.QuestionnaireDefinitions.Definition;
import com.sportacus.backend.questionnaire.dto.OptionResponse;
import com.sportacus.backend.questionnaire.dto.ProfileSuggestionResponse;
import com.sportacus.backend.questionnaire.dto.QuestionResponse;
import com.sportacus.backend.questionnaire.dto.QuestionnaireDetailResponse;
import com.sportacus.backend.questionnaire.dto.QuestionnaireGroupResponse;
import com.sportacus.backend.questionnaire.dto.QuestionnaireListResponse;
import com.sportacus.backend.questionnaire.dto.QuestionnaireSaveResponse;
import com.sportacus.backend.questionnaire.dto.QuestionnaireSummaryResponse;
import com.sportacus.backend.recommendation.RecommendationRepository;

@Service
public class QuestionnaireService {

    private static final int MAX_SUGGESTIONS = 3;

    /** Plantillas cuyos datos pesan más en el motor de recomendación (priorizan las sugerencias). */
    private static final Map<String, Integer> ENGINE_WEIGHT = Map.of(
            "objetivo", 3, "lesiones", 3, "entorno", 3, "experiencia", 3,
            "disponibilidad", 2, "personal", 2, "equipamiento", 2,
            "salud_habitos", 1, "alimentacion", 1);

    private static final Map<String, String> REASONS = Map.of(
            "personal", "Tu edad, peso y altura ajustan cargas, intensidad y riesgo.",
            "objetivo", "Tu objetivo y motivación definen qué disciplina te conviene.",
            "experiencia", "Tu nivel y actividad diaria evitan rutinas demasiado fáciles o duras.",
            "salud_habitos", "El sueño y el estrés ajustan la intensidad y la recuperación.",
            "alimentacion", "Tus hábitos de alimentación completan tu perfil de salud.",
            "lesiones", "Con tus lesiones y condiciones excluimos ejercicios que te perjudiquen.",
            "entorno", "Saber dónde puedes entrenar descarta disciplinas inviables.",
            "equipamiento", "Con tu equipamiento proponemos ejercicios que sí puedes hacer.",
            "disponibilidad", "Tus días y tiempo por sesión definen el tamaño de tu rutina.");

    private final QuestionnaireRepository repository;
    private final RecommendationRepository recommendationRepository;
    private final JdbcTemplate jdbcTemplate;

    public QuestionnaireService(QuestionnaireRepository repository, RecommendationRepository recommendationRepository,
            JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.recommendationRepository = recommendationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Avance por plantilla: campos contestados / campos totales. */
    private record Progress(Definition definition, int answered, int total) {
        int pending() {
            return total - answered;
        }
    }

    private List<Progress> progressOf(UUID userId) {
        List<Progress> result = new ArrayList<>();
        for (Definition def : QuestionnaireDefinitions.all().values()) {
            if (QuestionnaireDefinitions.isHidden(def.key())) {
                continue;
            }
            Set<String> answered = repository.answeredFields(userId, def.key());
            int count = (int) def.questionIds().stream().filter(answered::contains).count();
            result.add(new Progress(def, count, def.questions().size()));
        }
        return result;
    }

    public QuestionnaireListResponse listQuestionnaires(UUID userId) {
        Map<String, List<QuestionnaireSummaryResponse>> byGroup = new LinkedHashMap<>();
        List<Progress> progress = progressOf(userId);

        for (Progress p : progress) {
            Definition def = p.definition();
            int pct = pct(p.answered(), p.total());
            String status = p.answered() == 0 ? "sin_iniciar" : (p.answered() >= p.total() ? "completo" : "parcial");
            byGroup.computeIfAbsent(def.group(), g -> new ArrayList<>()).add(new QuestionnaireSummaryResponse(
                    def.key(), def.title(), def.description(), def.icon(), status, pct, p.answered(), p.total()));
        }

        List<QuestionnaireGroupResponse> groups = byGroup.entrySet().stream()
                .map(e -> new QuestionnaireGroupResponse(e.getKey(), e.getValue()))
                .toList();
        return new QuestionnaireListResponse(overallPct(progress), groups);
    }

    /** Fuerza del perfil (0-100): campos contestados sobre el total de campos de todas las plantillas. */
    public int profileStrengthPct(UUID userId) {
        return overallPct(progressOf(userId));
    }

    /** Hasta 3 plantillas con mayor ganancia pendiente para afinar la recomendación. */
    public List<ProfileSuggestionResponse> suggestions(UUID userId) {
        List<Progress> progress = progressOf(userId);
        int totalFields = progress.stream().mapToInt(Progress::total).sum();
        return progress.stream()
                .filter(p -> p.pending() > 0)
                .sorted(Comparator.comparingInt((Progress p) -> p.pending() * ENGINE_WEIGHT.getOrDefault(p.definition().key(), 1))
                        .reversed())
                .limit(MAX_SUGGESTIONS)
                .map(p -> new ProfileSuggestionResponse(p.definition().key(), p.definition().title(),
                        REASONS.getOrDefault(p.definition().key(), "Más datos, recomendaciones más afinadas."),
                        Math.max(1, pct(p.pending(), totalFields))))
                .toList();
    }

    public QuestionnaireDetailResponse getQuestionnaire(UUID userId, String key) {
        Definition def = QuestionnaireDefinitions.get(key);
        List<QuestionResponse> questions = def.questions();

        // El cuestionario de equipamiento carga sus "tags" desde el catálogo real
        // en vez de traerlas hardcodeadas en la definición estática.
        if ("equipamiento".equals(key)) {
            questions = withEquipmentTags(questions);
        }

        Map<String, Object> answers = repository.loadAnswers(userId, key);
        return new QuestionnaireDetailResponse(def.key(), def.title(), questions, answers);
    }

    /**
     * Guarda respuestas parciales e invalida la rutina vigente: se recalcula con el perfil
     * actualizado en el siguiente GET /api/routine.
     */
    public QuestionnaireSaveResponse saveAnswers(UUID userId, String key, Map<String, Object> answers) {
        QuestionnaireDefinitions.get(key); // valida que exista, lanza si no
        repository.saveAnswers(userId, key, answers == null ? Map.of() : answers);
        recommendationRepository.deleteExistingRoutes(userId);
        return new QuestionnaireSaveResponse(true, profileStrengthPct(userId));
    }

    private int overallPct(List<Progress> progress) {
        int answered = progress.stream().mapToInt(Progress::answered).sum();
        int total = progress.stream().mapToInt(Progress::total).sum();
        return pct(answered, total);
    }

    private int pct(int part, int whole) {
        return whole == 0 ? 0 : Math.round(part * 100f / whole);
    }

    private List<QuestionResponse> withEquipmentTags(List<QuestionResponse> questions) {
        List<EquipmentResponse> equipment = jdbcTemplate.query(
                "SELECT id, name, category FROM sportacus.equipment ORDER BY category, name",
                (rs, rowNum) -> new EquipmentResponse(rs.getString("id"), rs.getString("name"), rs.getString("category")));

        List<QuestionResponse> result = new ArrayList<>();
        for (QuestionResponse q : questions) {
            if ("q_equipment".equals(q.id())) {
                List<OptionResponse> options = equipment.stream()
                        .map(eq -> new OptionResponse(eq.id(), equipmentIcon(eq.category()), eq.name(), null))
                        .toList();
                // Para "tags" el frontend espera texto simple; usamos "options" con type "tags_catalog"
                // para poder llevar el id real del equipo (necesario para guardar en user_equipment).
                result.add(new QuestionResponse(q.id(), q.prompt(), q.hint(), "tags_catalog", options, null, null, null, null));
            } else {
                result.add(q);
            }
        }
        return result;
    }

    private String equipmentIcon(String category) {
        return switch (category) {
            case "fuerza" -> "🏋️";
            case "cardio" -> "🚴";
            case "movilidad" -> "🧘";
            default -> "🎒";
        };
    }
}
