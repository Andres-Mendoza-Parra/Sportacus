package com.sportacus.backend.questionnaire;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Persiste y recupera las respuestas de cada plantilla de datos en las tablas reales del
 * esquema (user_profile, user_goal, nutrition_profile, injury, condition, user_resources,
 * user_equipment, body_measurement). No hay una tabla "respuestas de cuestionario"; cada
 * campo escribe directamente en la tabla de dominio que le corresponde.
 *
 * Todo campo es opcional y el guardado es PARCIAL: un campo ausente del cuerpo no se toca.
 *
 * Qué campos están "contestados" se guarda aparte en progress_record
 * (metric_key='campo_respondido', context='<cuestionario>:<campo>'), porque varias columnas
 * tienen valor por defecto (o "ninguna lesión" no deja filas) y no se podría distinguir
 * "contestó que no" de "no contestó". Para usuarios anteriores a este esquema (sin
 * registros de campo) se infiere de los datos ya guardados.
 *
 * Los datos que no tienen columna propia (días por semana, días preferidos, momento del día)
 * se guardan en progress_record con metric_key='pref_<campo>' y el valor textual en context.
 */
@Repository
public class QuestionnaireRepository {

    private static final String INJURY_MARKER = "cuestionario_lesiones";
    private static final List<String> CONDITION_TAGS = List.of(
            "Hipertensión", "Diabetes", "Osteoporosis", "Asma", "Artritis", "Cardiopatía");
    private static final String SURGERY_CONDITION_TYPE = "Post-operatorio reciente";

    private static final String FIELD_METRIC = "campo_respondido";
    private static final String PREF_PREFIX = "pref_";

    /** Columnas booleanas con DEFAULT en BD: un "no" por defecto no cuenta como respuesta. */
    private static final Set<String> DEFAULTED_FIELDS = Set.of(
            "q_intermittent_fasting", "q_park_nearby", "q_outdoor_safe", "q_travels");

    private static final Map<String, String> GOAL_LABEL_TO_SLUG = Map.of(
            "Ganar músculo", "ganancia_muscular",
            "Perder grasa", "perdida_grasa",
            "Rendimiento deportivo", "rendimiento_deportivo",
            "Bienestar general", "bienestar_general",
            "Flexibilidad", "flexibilidad",
            "Rehabilitación", "rehabilitacion",
            "Crear el hábito", "habito_activo",
            "Gestionar el estrés", "gestion_estres");

    private static final Set<String> GOALS = Set.copyOf(GOAL_LABEL_TO_SLUG.values());
    private static final Set<String> HORIZONS = Set.of("corto", "medio", "largo");
    private static final Set<String> WORK_ACTIVITY = Set.of("sedentario", "leve", "moderado", "activo", "muy_activo");
    private static final Set<String> BUDGETS = Set.of("ninguno", "bajo", "medio", "alto");
    private static final Set<String> HOME_SPACE = Set.of("sin_espacio", "pequeno", "mediano", "grande");
    private static final Set<String> SESSION_TIME = Set.of("menos_30", "30_45", "45_60", "60_90", "mas_90");
    private static final Set<String> SOCIAL = Set.of("solo", "grupo", "mixto");
    private static final Set<String> FOOD_BUDGET = Set.of("bajo", "medio", "alto");
    private static final Set<String> SEVERITIES = Set.of("leve", "moderada", "grave");

    private final JdbcTemplate jdbcTemplate;

    public QuestionnaireRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ───────────────────────── Campos contestados ─────────────────────────

    private Set<String> recordedFields(UUID userId, String key) {
        List<String> contexts = jdbcTemplate.query("""
                SELECT context FROM sportacus.progress_record
                WHERE user_id = ? AND metric_key = ? AND context LIKE ?
                """, (rs, rowNum) -> rs.getString("context"), userId, FIELD_METRIC, key + ":%");
        Set<String> fields = new HashSet<>();
        String prefix = key + ":";
        for (String context : contexts) {
            if (context != null && context.startsWith(prefix)) {
                fields.add(context.substring(prefix.length()));
            }
        }
        return fields;
    }

    private boolean hasLegacyCompletionMarker(UUID userId, String key) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sportacus.progress_record
                WHERE user_id = ? AND metric_key = 'cuestionario_completado' AND context = ?
                """, Integer.class, userId, key);
        return count != null && count > 0;
    }

    /** Ids de los campos contestados de una plantilla. */
    public Set<String> answeredFields(UUID userId, String key) {
        Set<String> recorded = recordedFields(userId, key);
        if (!recorded.isEmpty()) {
            return recorded;
        }
        return inferLegacyFields(userId, key, loadRawAnswers(userId, key));
    }

    private Set<String> inferLegacyFields(UUID userId, String key, Map<String, Object> raw) {
        boolean legacyCompleted = hasLegacyCompletionMarker(userId, key);
        Set<String> fields = new HashSet<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (DEFAULTED_FIELDS.contains(entry.getKey())) {
                continue;
            }
            if (legacyCompleted || isPresent(entry.getValue())) {
                fields.add(entry.getKey());
            }
        }
        return fields;
    }

    private void recordAnswered(UUID userId, String key, Collection<String> fieldIds) {
        for (String fieldId : fieldIds) {
            String context = key + ":" + fieldId;
            jdbcTemplate.update("""
                    DELETE FROM sportacus.progress_record
                    WHERE user_id = ? AND metric_key = ? AND context = ?
                    """, userId, FIELD_METRIC, context);
            jdbcTemplate.update("""
                    INSERT INTO sportacus.progress_record (user_id, metric_key, value, recorded_at, context)
                    VALUES (?, ?, 1, CURRENT_DATE, ?)
                    """, userId, FIELD_METRIC, context);
        }
    }

    private boolean isPresent(Object value) {
        if (value == null) return false;
        if (value instanceof Collection<?> c) return !c.isEmpty();
        if (value instanceof String s) return !s.isBlank();
        return true;
    }

    // ───────────────────────── Preferencias sin columna propia ─────────────────────────

    private Map<String, String> loadPrefs(UUID userId) {
        Map<String, String> prefs = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT metric_key, context FROM sportacus.progress_record
                WHERE user_id = ? AND metric_key LIKE 'pref%' ORDER BY recorded_at, id
                """, rs -> {
            String metricKey = rs.getString("metric_key");
            if (metricKey != null && metricKey.startsWith(PREF_PREFIX)) {
                prefs.put(metricKey.substring(PREF_PREFIX.length()), rs.getString("context"));
            }
        }, userId);
        return prefs;
    }

    private void savePref(UUID userId, String fieldId, String textValue) {
        String metricKey = PREF_PREFIX + fieldId;
        jdbcTemplate.update("DELETE FROM sportacus.progress_record WHERE user_id = ? AND metric_key = ?",
                userId, metricKey);
        Number numeric = asNumber(textValue);
        jdbcTemplate.update("""
                INSERT INTO sportacus.progress_record (user_id, metric_key, value, recorded_at, context)
                VALUES (?, ?, ?, CURRENT_DATE, ?)
                """, userId, metricKey, numeric == null ? 1 : numeric, textValue);
    }

    // ───────────────────────── Carga (prefill) ─────────────────────────

    /** Respuestas ya guardadas, limitadas a los campos que el usuario contestó. */
    public Map<String, Object> loadAnswers(UUID userId, String key) {
        Map<String, Object> raw = loadRawAnswers(userId, key);
        Set<String> answered = answeredFields(userId, key);
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (answered.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    private Map<String, Object> loadRawAnswers(UUID userId, String key) {
        return switch (key) {
            case "enfoque" -> loadEnfoque(userId);
            case "personal" -> loadPersonal(userId);
            case "objetivo" -> loadObjetivo(userId);
            case "experiencia" -> loadExperiencia(userId);
            case "salud_habitos" -> loadSaludHabitos(userId);
            case "alimentacion" -> loadAlimentacion(userId);
            case "lesiones" -> loadLesiones(userId);
            case "entorno" -> loadEntorno(userId);
            case "equipamiento" -> loadEquipamiento(userId);
            case "disponibilidad" -> loadDisponibilidad(userId);
            default -> Map.of();
        };
    }

    /** Onboarding corto: los mismos campos que "objetivo"/"experiencia"/"entorno", sin duplicarlos. */
    private Map<String, Object> loadEnfoque(UUID userId) {
        Map<String, Object> answers = new LinkedHashMap<>();
        List<Map<String, Object>> goalRows = jdbcTemplate.queryForList("""
                SELECT primary_goal FROM sportacus.user_goal
                WHERE user_id = ? AND is_active = TRUE ORDER BY created_at DESC LIMIT 1
                """, userId);
        if (!goalRows.isEmpty()) {
            putIfPresent(answers, "q_primary_goal", goalRows.get(0).get("primary_goal"));
        }
        List<Map<String, Object>> profileRows = jdbcTemplate.queryForList(
                "SELECT experience_level FROM sportacus.user_profile WHERE user_id = ?", userId);
        if (!profileRows.isEmpty() && profileRows.get(0).get("experience_level") != null) {
            answers.put("q_experience_level", String.valueOf(profileRows.get(0).get("experience_level")));
        }
        List<Map<String, Object>> resourceRows = jdbcTemplate.queryForList(
                "SELECT available_environments FROM sportacus.user_resources WHERE user_id = ?", userId);
        if (!resourceRows.isEmpty()) {
            answers.put("q_environments", toStringList(resourceRows.get(0).get("available_environments")).stream()
                    .map(this::slugToEnvironment).toList());
        }
        return answers;
    }

    private Map<String, Object> loadPersonal(UUID userId) {
        String sql = "SELECT gender, birth_date, weight_kg, height_cm, body_fat_pct FROM sportacus.user_profile WHERE user_id = ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId);
        Map<String, Object> answers = new LinkedHashMap<>();
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            if (row.get("birth_date") != null) {
                LocalDate birthDate = ((java.sql.Date) row.get("birth_date")).toLocalDate();
                answers.put("q_age", Period.between(birthDate, LocalDate.now()).getYears());
            }
            putIfPresent(answers, "q_weight", row.get("weight_kg"));
            putIfPresent(answers, "q_height", row.get("height_cm"));
            putIfPresent(answers, "q_gender", row.get("gender"));
            putIfPresent(answers, "q_body_fat", row.get("body_fat_pct"));
        }
        List<Object> waist = jdbcTemplate.query("""
                SELECT waist_cm FROM sportacus.body_measurement
                WHERE user_id = ? AND waist_cm IS NOT NULL ORDER BY measured_at DESC, id DESC LIMIT 1
                """, (rs, rowNum) -> rs.getObject("waist_cm"), userId);
        if (!waist.isEmpty()) {
            answers.put("q_waist", waist.get(0));
        }
        return answers;
    }

    private Map<String, Object> loadObjetivo(UUID userId) {
        String sql = """
                SELECT primary_goal, time_horizon, secondary_goals, target_weight_kg, perceived_fitness_1_10,
                       motivation_1_10, confidence_1_10, motivation_type
                FROM sportacus.user_goal
                WHERE user_id = ? AND is_active = TRUE ORDER BY created_at DESC LIMIT 1
                """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId);
        Map<String, Object> answers = new LinkedHashMap<>();
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            putIfPresent(answers, "q_primary_goal", row.get("primary_goal"));
            putIfPresent(answers, "q_time_horizon", row.get("time_horizon"));
            List<String> secondary = new ArrayList<>();
            for (String slug : toStringList(row.get("secondary_goals"))) {
                GOAL_LABEL_TO_SLUG.forEach((label, s) -> {
                    if (s.equals(slug)) secondary.add(label);
                });
            }
            answers.put("q_secondary_goals", secondary);
            putIfPresent(answers, "q_target_weight", row.get("target_weight_kg"));
            putIfPresent(answers, "q_perceived_fitness", row.get("perceived_fitness_1_10"));
            putIfPresent(answers, "q_motivation", row.get("motivation_1_10"));
            putIfPresent(answers, "q_confidence", row.get("confidence_1_10"));
            putIfPresent(answers, "q_motivation_type", row.get("motivation_type"));
        }
        return answers;
    }

    private Map<String, Object> loadExperiencia(UUID userId) {
        String sql = """
                SELECT experience_level, years_active, current_disciplines, daily_steps_approx, seated_hours_day
                FROM sportacus.user_profile WHERE user_id = ?
                """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId);
        Map<String, Object> answers = new LinkedHashMap<>();
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            if (row.get("experience_level") != null) {
                answers.put("q_experience_level", String.valueOf(row.get("experience_level")));
            }
            putIfPresent(answers, "q_years_active", row.get("years_active"));
            answers.put("q_disciplines", toStringList(row.get("current_disciplines")));
            if (row.get("daily_steps_approx") instanceof Number steps) {
                answers.put("q_daily_steps", Math.round(steps.doubleValue() / 1000));
            }
            putIfPresent(answers, "q_seated_hours", row.get("seated_hours_day"));
        }
        return answers;
    }

    private Map<String, Object> loadSaludHabitos(UUID userId) {
        String sql = """
                SELECT sleep_hours_avg, sleep_quality_1_5, stress_level_1_5, work_activity_type
                FROM sportacus.user_profile WHERE user_id = ?
                """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId);
        Map<String, Object> answers = new LinkedHashMap<>();
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            putIfPresent(answers, "q_sleep_hours", row.get("sleep_hours_avg"));
            if (row.get("sleep_quality_1_5") != null) {
                answers.put("q_sleep_quality", String.valueOf(row.get("sleep_quality_1_5")));
            }
            if (row.get("stress_level_1_5") != null) {
                answers.put("q_stress_level", String.valueOf(row.get("stress_level_1_5")));
            }
            putIfPresent(answers, "q_work_activity", row.get("work_activity_type"));
        }
        return answers;
    }

    private Map<String, Object> loadAlimentacion(UUID userId) {
        String sql = """
                SELECT diet_type, daily_water_liters, allergies, meal_count_per_day, intermittent_fasting,
                       food_budget_level
                FROM sportacus.nutrition_profile WHERE user_id = ?
                """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId);
        Map<String, Object> answers = new LinkedHashMap<>();
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            putIfPresent(answers, "q_diet_type", row.get("diet_type"));
            putIfPresent(answers, "q_water", row.get("daily_water_liters"));
            putIfPresent(answers, "q_meal_count", row.get("meal_count_per_day"));
            answers.put("q_intermittent_fasting", Boolean.TRUE.equals(row.get("intermittent_fasting")) ? "si" : "no");
            putIfPresent(answers, "q_food_budget", row.get("food_budget_level"));
            answers.put("q_allergies", toStringList(row.get("allergies")));
        }
        return answers;
    }

    private Map<String, Object> loadLesiones(UUID userId) {
        Map<String, Object> answers = new LinkedHashMap<>();
        List<Map<String, Object>> injuries = jdbcTemplate.queryForList(
                "SELECT location, severity FROM sportacus.injury WHERE user_id = ? AND medical_notes = ?",
                userId, INJURY_MARKER);
        if (!injuries.isEmpty()) {
            answers.put("q_injury_status", injuries.get(0).get("location"));
            putIfPresent(answers, "q_injury_severity", injuries.get(0).get("severity"));
        }
        List<String> conditionTypes = jdbcTemplate.query(
                "SELECT type FROM sportacus.condition WHERE user_id = ?",
                (rs, rowNum) -> rs.getString("type"), userId);
        List<String> selectedTags = conditionTypes.stream().filter(CONDITION_TAGS::contains).toList();
        answers.put("q_conditions", selectedTags);
        if (conditionTypes.contains(SURGERY_CONDITION_TYPE)) {
            answers.put("q_recent_surgery", "si");
        } else if (!conditionTypes.isEmpty() || !injuries.isEmpty()) {
            answers.put("q_recent_surgery", "no");
        }
        return answers;
    }

    private Map<String, Object> loadEntorno(UUID userId) {
        String sql = """
                SELECT available_environments, home_space_size, budget_membership, gym_distance_min, park_nearby,
                       outdoor_safe, climate_type, travels_frequently, social_preference
                FROM sportacus.user_resources WHERE user_id = ?
                """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId);
        Map<String, Object> answers = new LinkedHashMap<>();
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            answers.put("q_environments", toStringList(row.get("available_environments")).stream()
                    .map(this::slugToEnvironment).toList());
            putIfPresent(answers, "q_home_space", row.get("home_space_size"));
            putIfPresent(answers, "q_budget_membership", row.get("budget_membership"));
            putIfPresent(answers, "q_gym_distance", row.get("gym_distance_min"));
            answers.put("q_park_nearby", Boolean.TRUE.equals(row.get("park_nearby")) ? "si" : "no");
            answers.put("q_outdoor_safe", Boolean.FALSE.equals(row.get("outdoor_safe")) ? "no" : "si");
            putIfPresent(answers, "q_climate", row.get("climate_type"));
            answers.put("q_travels", Boolean.TRUE.equals(row.get("travels_frequently")) ? "si" : "no");
            putIfPresent(answers, "q_social_preference", row.get("social_preference"));
        }
        return answers;
    }

    private Map<String, Object> loadEquipamiento(UUID userId) {
        Map<String, Object> answers = new LinkedHashMap<>();
        List<String> equipmentIds = jdbcTemplate.query("""
                SELECT ue.equipment_id::text AS equipment_id
                FROM sportacus.user_equipment ue
                JOIN sportacus.user_resources ur ON ur.id = ue.user_resources_id
                WHERE ur.user_id = ?
                """, (rs, rowNum) -> rs.getString("equipment_id"), userId);
        answers.put("q_equipment", equipmentIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT budget_equipment FROM sportacus.user_resources WHERE user_id = ?", userId);
        if (!rows.isEmpty()) {
            putIfPresent(answers, "q_budget_equipment", rows.get(0).get("budget_equipment"));
        }
        return answers;
    }

    private Map<String, Object> loadDisponibilidad(UUID userId) {
        Map<String, Object> answers = new LinkedHashMap<>();
        Map<String, String> prefs = loadPrefs(userId);
        Integer days = asInt(prefs.get("q_days_per_week"));
        if (days != null) {
            answers.put("q_days_per_week", days);
        }
        String preferredDays = prefs.get("q_preferred_days");
        if (preferredDays != null && !preferredDays.isBlank()) {
            answers.put("q_preferred_days", List.of(preferredDays.split(",")));
        }
        putIfPresent(answers, "q_time_of_day", prefs.get("q_time_of_day"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT time_per_session FROM sportacus.user_resources WHERE user_id = ?", userId);
        if (!rows.isEmpty()) {
            putIfPresent(answers, "q_time_per_session", rows.get(0).get("time_per_session"));
        }
        return answers;
    }

    // ───────────────────────── Guardado ─────────────────────────

    /**
     * Guarda las respuestas recibidas (parcial: lo omitido no se toca) y registra qué campos
     * quedaron contestados. Un valor null cuenta como "omitido".
     */
    @Transactional
    public void saveAnswers(UUID userId, String key, Map<String, Object> answers) {
        QuestionnaireDefinitions.Definition definition = QuestionnaireDefinitions.get(key);

        // Usuarios previos al registro por campo: se fija primero lo que ya tenían contestado
        // para no "olvidarlo" al empezar a registrar solo los campos nuevos.
        if (recordedFields(userId, key).isEmpty()) {
            Set<String> legacy = inferLegacyFields(userId, key, loadRawAnswers(userId, key));
            recordAnswered(userId, key, legacy);
        }

        switch (key) {
            case "enfoque" -> saveEnfoque(userId, answers);
            case "personal" -> savePersonal(userId, answers);
            case "objetivo" -> saveObjetivo(userId, answers);
            case "experiencia" -> saveExperiencia(userId, answers);
            case "salud_habitos" -> saveSaludHabitos(userId, answers);
            case "alimentacion" -> saveAlimentacion(userId, answers);
            case "lesiones" -> saveLesiones(userId, answers);
            case "entorno" -> saveEntorno(userId, answers);
            case "equipamiento" -> saveEquipamiento(userId, answers);
            case "disponibilidad" -> saveDisponibilidad(userId, answers);
            default -> throw new IllegalArgumentException("Cuestionario no encontrado: " + key);
        }

        List<String> known = definition.questionIds();
        List<String> answeredNow = answers.entrySet().stream()
                .filter(e -> e.getValue() != null && known.contains(e.getKey()))
                .map(Map.Entry::getKey)
                .toList();
        recordAnswered(userId, key, answeredNow);
    }

    /** Onboarding corto: escribe en las mismas columnas que "objetivo"/"experiencia"/"entorno". */
    private void saveEnfoque(UUID userId, Map<String, Object> answers) {
        String primaryGoal = oneOf(answers, "q_primary_goal", GOALS);
        if (primaryGoal != null) {
            List<UUID> existing = jdbcTemplate.query("""
                    SELECT id FROM sportacus.user_goal WHERE user_id = ? AND is_active = TRUE
                    ORDER BY created_at DESC LIMIT 1
                    """, (rs, rowNum) -> (UUID) rs.getObject("id"), userId);
            if (!existing.isEmpty()) {
                updateById("user_goal", existing.get(0), Map.of("primary_goal", primaryGoal));
            } else {
                jdbcTemplate.update("INSERT INTO sportacus.user_goal (user_id, primary_goal) VALUES (?, ?)",
                        userId, primaryGoal);
            }
        }

        Integer experience = clampOrNull(asInt(answers.get("q_experience_level")), 0, 4);
        if (experience != null) {
            upsertByUserId("user_profile", userId, Map.of("experience_level", experience));
        }

        if (answers.get("q_environments") instanceof List<?>) {
            List<String> environments = asStringList(answers.get("q_environments")).stream()
                    .map(this::environmentToSlug)
                    .toList();
            upsertTextArrayColumn("user_resources", userId, "available_environments", environments);
        }
    }

    private void savePersonal(UUID userId, Map<String, Object> answers) {
        Map<String, Object> columns = new LinkedHashMap<>();
        Integer age = asInt(answers.get("q_age"));
        if (age != null) {
            columns.put("birth_date", LocalDate.now().minusYears(clamp(age, 5, 110)));
        }
        putColumnIfPresent(columns, "weight_kg", asNumber(answers.get("q_weight")));
        putColumnIfPresent(columns, "height_cm", asNumber(answers.get("q_height")));
        putColumnIfPresent(columns, "gender", answers.get("q_gender"));
        Integer bodyFat = asInt(answers.get("q_body_fat"));
        if (bodyFat != null) {
            columns.put("body_fat_pct", clamp(bodyFat, 0, 70));
        }
        upsertByUserId("user_profile", userId, columns);

        Number waist = asNumber(answers.get("q_waist"));
        if (waist != null) {
            jdbcTemplate.update("""
                    DELETE FROM sportacus.body_measurement
                    WHERE user_id = ? AND measured_at = CURRENT_DATE AND method = 'cuestionario'
                    """, userId);
            jdbcTemplate.update("""
                    INSERT INTO sportacus.body_measurement (user_id, measured_at, waist_cm, method)
                    VALUES (?, CURRENT_DATE, ?, 'cuestionario')
                    """, userId, waist);
        }
    }

    private void saveObjetivo(UUID userId, Map<String, Object> answers) {
        String primaryGoal = oneOf(answers, "q_primary_goal", GOALS);
        Map<String, Object> columns = new LinkedHashMap<>();
        putColumnIfPresent(columns, "primary_goal", primaryGoal);
        putColumnIfPresent(columns, "time_horizon", oneOf(answers, "q_time_horizon", HORIZONS));
        putColumnIfPresent(columns, "target_weight_kg", asNumber(answers.get("q_target_weight")));
        putColumnIfPresent(columns, "perceived_fitness_1_10", clampOrNull(asInt(answers.get("q_perceived_fitness")), 1, 10));
        putColumnIfPresent(columns, "motivation_1_10", clampOrNull(asInt(answers.get("q_motivation")), 1, 10));
        putColumnIfPresent(columns, "confidence_1_10", clampOrNull(asInt(answers.get("q_confidence")), 1, 10));
        putColumnIfPresent(columns, "motivation_type", answers.get("q_motivation_type"));
        boolean hasSecondary = answers.get("q_secondary_goals") instanceof List<?>;

        if (columns.isEmpty() && !hasSecondary) {
            return;
        }

        List<UUID> existing = jdbcTemplate.query("""
                SELECT id FROM sportacus.user_goal WHERE user_id = ? AND is_active = TRUE
                ORDER BY created_at DESC LIMIT 1
                """, (rs, rowNum) -> (UUID) rs.getObject("id"), userId);

        UUID goalId;
        if (!existing.isEmpty()) {
            goalId = existing.get(0);
        } else {
            // primary_goal es obligatorio en BD: si el usuario aún no lo eligió se usa el neutro
            // "bienestar_general" (no cuenta como contestado hasta que lo elija).
            goalId = jdbcTemplate.queryForObject("""
                    INSERT INTO sportacus.user_goal (user_id, primary_goal) VALUES (?, ?) RETURNING id
                    """, UUID.class, userId, primaryGoal != null ? primaryGoal : "bienestar_general");
        }
        updateById("user_goal", goalId, columns);

        if (hasSecondary) {
            List<String> slugs = asStringList(answers.get("q_secondary_goals")).stream()
                    .map(GOAL_LABEL_TO_SLUG::get)
                    .filter(s -> s != null)
                    .distinct()
                    .toList();
            jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE sportacus.user_goal SET secondary_goals = ? WHERE id = ?")) {
                    ps.setArray(1, conn.createArrayOf("text", slugs.toArray()));
                    ps.setObject(2, goalId);
                    ps.executeUpdate();
                }
                return null;
            });
        }
    }

    private void saveExperiencia(UUID userId, Map<String, Object> answers) {
        Map<String, Object> columns = new LinkedHashMap<>();
        putColumnIfPresent(columns, "experience_level", clampOrNull(asInt(answers.get("q_experience_level")), 0, 4));
        putColumnIfPresent(columns, "years_active", clampOrNull(asInt(answers.get("q_years_active")), 0, 80));
        Integer steps = asInt(answers.get("q_daily_steps"));
        if (steps != null) {
            columns.put("daily_steps_approx", clamp(steps, 0, 60) * 1000);
        }
        putColumnIfPresent(columns, "seated_hours_day", asNumber(answers.get("q_seated_hours")));
        upsertByUserId("user_profile", userId, columns);

        if (answers.get("q_disciplines") instanceof List<?>) {
            List<String> disciplines = asStringList(answers.get("q_disciplines")).stream()
                    .filter(d -> !"Ninguna".equalsIgnoreCase(d))
                    .toList();
            upsertTextArrayColumn("user_profile", userId, "current_disciplines", disciplines);
        }
    }

    private void saveSaludHabitos(UUID userId, Map<String, Object> answers) {
        Map<String, Object> columns = new LinkedHashMap<>();
        putColumnIfPresent(columns, "sleep_hours_avg", asNumber(answers.get("q_sleep_hours")));
        putColumnIfPresent(columns, "sleep_quality_1_5", clampOrNull(asInt(answers.get("q_sleep_quality")), 1, 5));
        putColumnIfPresent(columns, "stress_level_1_5", clampOrNull(asInt(answers.get("q_stress_level")), 1, 5));
        putColumnIfPresent(columns, "work_activity_type", oneOf(answers, "q_work_activity", WORK_ACTIVITY));
        upsertByUserId("user_profile", userId, columns);
    }

    private void saveAlimentacion(UUID userId, Map<String, Object> answers) {
        Map<String, Object> columns = new LinkedHashMap<>();
        putColumnIfPresent(columns, "diet_type", answers.get("q_diet_type"));
        putColumnIfPresent(columns, "daily_water_liters", asNumber(answers.get("q_water")));
        putColumnIfPresent(columns, "meal_count_per_day", clampOrNull(asInt(answers.get("q_meal_count")), 1, 12));
        putColumnIfPresent(columns, "intermittent_fasting", asYesNo(answers.get("q_intermittent_fasting")));
        putColumnIfPresent(columns, "food_budget_level", oneOf(answers, "q_food_budget", FOOD_BUDGET));
        upsertByUserId("nutrition_profile", userId, columns);

        if (answers.get("q_allergies") instanceof List<?>) {
            List<String> allergies = asStringList(answers.get("q_allergies")).stream()
                    .filter(a -> !"Ninguna".equalsIgnoreCase(a))
                    .toList();
            upsertTextArrayColumn("nutrition_profile", userId, "allergies", allergies);
        }
    }

    private void saveLesiones(UUID userId, Map<String, Object> answers) {
        String severity = oneOf(answers, "q_injury_severity", SEVERITIES);
        String injuryStatus = asString(answers.get("q_injury_status"));

        if (injuryStatus != null) {
            jdbcTemplate.update("DELETE FROM sportacus.injury WHERE user_id = ? AND medical_notes = ?", userId, INJURY_MARKER);
            if (!"sin_lesion".equals(injuryStatus)) {
                jdbcTemplate.update("""
                        INSERT INTO sportacus.injury (user_id, location, severity, current_status, medical_notes)
                        VALUES (?, ?, ?, 'activa', ?)
                        """, userId, injuryStatus, severity != null ? severity : "moderada", INJURY_MARKER);
            }
        } else if (severity != null) {
            jdbcTemplate.update("UPDATE sportacus.injury SET severity = ? WHERE user_id = ? AND medical_notes = ?",
                    severity, userId, INJURY_MARKER);
        }

        if (answers.get("q_conditions") instanceof List<?>) {
            jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM sportacus.condition WHERE user_id = ? AND type = ANY(?)")) {
                    ps.setObject(1, userId);
                    ps.setArray(2, conn.createArrayOf("text", CONDITION_TAGS.toArray()));
                    ps.executeUpdate();
                }
                return null;
            });
            for (String conditionType : asStringList(answers.get("q_conditions")).stream()
                    .filter(CONDITION_TAGS::contains).toList()) {
                jdbcTemplate.update("INSERT INTO sportacus.condition (user_id, type) VALUES (?, ?)", userId, conditionType);
            }
        }

        String surgery = asString(answers.get("q_recent_surgery"));
        if (surgery != null) {
            jdbcTemplate.update("DELETE FROM sportacus.condition WHERE user_id = ? AND type = ?",
                    userId, SURGERY_CONDITION_TYPE);
            if ("si".equals(surgery)) {
                jdbcTemplate.update("""
                        INSERT INTO sportacus.condition (user_id, type, exercise_impact)
                        VALUES (?, ?, 'alto')
                        """, userId, SURGERY_CONDITION_TYPE);
            }
        }
    }

    private void saveEntorno(UUID userId, Map<String, Object> answers) {
        Map<String, Object> columns = new LinkedHashMap<>();
        putColumnIfPresent(columns, "home_space_size", oneOf(answers, "q_home_space", HOME_SPACE));
        putColumnIfPresent(columns, "budget_membership", oneOf(answers, "q_budget_membership", BUDGETS));
        putColumnIfPresent(columns, "gym_distance_min", clampOrNull(asInt(answers.get("q_gym_distance")), 0, 600));
        putColumnIfPresent(columns, "park_nearby", asYesNo(answers.get("q_park_nearby")));
        putColumnIfPresent(columns, "outdoor_safe", asYesNo(answers.get("q_outdoor_safe")));
        putColumnIfPresent(columns, "climate_type", answers.get("q_climate"));
        putColumnIfPresent(columns, "travels_frequently", asYesNo(answers.get("q_travels")));
        putColumnIfPresent(columns, "social_preference", oneOf(answers, "q_social_preference", SOCIAL));
        upsertByUserId("user_resources", userId, columns);

        if (answers.get("q_environments") instanceof List<?>) {
            List<String> environments = asStringList(answers.get("q_environments")).stream()
                    .map(this::environmentToSlug)
                    .toList();
            upsertTextArrayColumn("user_resources", userId, "available_environments", environments);
        }
    }

    private void saveEquipamiento(UUID userId, Map<String, Object> answers) {
        Map<String, Object> columns = new LinkedHashMap<>();
        putColumnIfPresent(columns, "budget_equipment", oneOf(answers, "q_budget_equipment", BUDGETS));
        upsertByUserId("user_resources", userId, columns);

        if (!(answers.get("q_equipment") instanceof List<?>)) {
            return;
        }
        // Se asegura la fila de recursos (puede no existir si solo se contestó el equipo).
        upsertByUserId("user_resources", userId, Map.of("updated_at", java.sql.Timestamp.from(java.time.Instant.now())));
        UUID resourcesId = jdbcTemplate.queryForObject(
                "SELECT id FROM sportacus.user_resources WHERE user_id = ?", UUID.class, userId);
        jdbcTemplate.update("DELETE FROM sportacus.user_equipment WHERE user_resources_id = ?", resourcesId);
        for (String equipmentId : asStringList(answers.get("q_equipment"))) {
            try {
                jdbcTemplate.update("""
                        INSERT INTO sportacus.user_equipment (user_resources_id, equipment_id, owned)
                        VALUES (?, ?::uuid, true)
                        """, resourcesId, equipmentId);
            } catch (Exception ignored) {
                // id de equipo inválido/obsoleto: se ignora en vez de romper el guardado completo
            }
        }
    }

    private void saveDisponibilidad(UUID userId, Map<String, Object> answers) {
        Integer days = clampOrNull(asInt(answers.get("q_days_per_week")), 1, 7);
        if (days != null) {
            savePref(userId, "q_days_per_week", String.valueOf(days));
        }
        if (answers.get("q_preferred_days") instanceof List<?>) {
            List<String> preferred = asStringList(answers.get("q_preferred_days")).stream()
                    .filter(d -> List.of("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom").contains(d))
                    .distinct()
                    .toList();
            savePref(userId, "q_preferred_days", String.join(",", preferred));
        }
        String timeOfDay = asString(answers.get("q_time_of_day"));
        if (timeOfDay != null) {
            savePref(userId, "q_time_of_day", timeOfDay);
        }
        String perSession = oneOf(answers, "q_time_per_session", SESSION_TIME);
        if (perSession != null) {
            Map<String, Object> columns = new LinkedHashMap<>();
            columns.put("time_per_session", perSession);
            upsertByUserId("user_resources", userId, columns);
        }
    }

    private String slugToEnvironment(String slug) {
        return switch (slug) {
            case "gimnasio" -> "Gimnasio";
            case "casa" -> "Casa";
            case "parque" -> "Parque";
            case "centro_deportivo" -> "Centro deportivo";
            default -> slug;
        };
    }

    private String environmentToSlug(String label) {
        return switch (label) {
            case "Gimnasio" -> "gimnasio";
            case "Casa" -> "casa";
            case "Parque" -> "parque";
            case "Centro deportivo" -> "centro_deportivo";
            default -> label.toLowerCase();
        };
    }

    // ───────────────────────── Helpers genéricos ─────────────────────────

    /** INSERT ... ON CONFLICT (user_id) DO UPDATE, solo para las columnas presentes en {@code columns}. */
    private void upsertByUserId(String table, UUID userId, Map<String, Object> columns) {
        if (columns.isEmpty()) {
            return;
        }
        List<String> cols = new ArrayList<>(columns.keySet());
        String colList = String.join(", ", cols);
        String placeholders = cols.stream().map(c -> "?").collect(Collectors.joining(", "));
        String updateList = cols.stream().map(c -> c + " = EXCLUDED." + c).collect(Collectors.joining(", "));
        String sql = "INSERT INTO sportacus." + table + " (user_id, " + colList + ") VALUES (?, " + placeholders + ") "
                + "ON CONFLICT (user_id) DO UPDATE SET " + updateList;
        List<Object> params = new ArrayList<>();
        params.add(userId);
        for (String col : cols) {
            params.add(columns.get(col));
        }
        jdbcTemplate.update(sql, params.toArray());
    }

    private void updateById(String table, UUID id, Map<String, Object> columns) {
        if (columns.isEmpty()) {
            return;
        }
        List<String> cols = new ArrayList<>(columns.keySet());
        String setList = cols.stream().map(c -> c + " = ?").collect(Collectors.joining(", "));
        List<Object> params = new ArrayList<>();
        for (String col : cols) {
            params.add(columns.get(col));
        }
        params.add(id);
        jdbcTemplate.update("UPDATE sportacus." + table + " SET " + setList + " WHERE id = ?", params.toArray());
    }

    /** Upsert de una sola columna text[] en una tabla con user_id único. Crea la fila si no existe. */
    private void upsertTextArrayColumn(String table, UUID userId, String column, List<String> values) {
        String sql = "INSERT INTO sportacus." + table + " (user_id, " + column + ") VALUES (?, ?) "
                + "ON CONFLICT (user_id) DO UPDATE SET " + column + " = EXCLUDED." + column;
        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, userId);
                ps.setArray(2, conn.createArrayOf("text", values.toArray()));
                ps.executeUpdate();
            }
            return null;
        });
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private void putColumnIfPresent(Map<String, Object> columns, String column, Object value) {
        if (value != null) {
            columns.put(column, value);
        }
    }

    private List<String> toStringList(Object pgArray) {
        if (pgArray == null) {
            return List.of();
        }
        try {
            Object[] values = (Object[]) ((Array) pgArray).getArray();
            List<String> result = new ArrayList<>();
            for (Object v : values) {
                result.add(String.valueOf(v));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toList());
        }
        return List.of();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** Devuelve el valor si está en {@code allowed}; null si se omitió; 400 si es inválido. */
    private String oneOf(Map<String, Object> answers, String field, Set<String> allowed) {
        String value = asString(answers.get(field));
        if (value == null) {
            return null;
        }
        if (!allowed.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor no válido para " + field + ": " + value);
        }
        return value;
    }

    private Boolean asYesNo(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean b) return b;
        String s = String.valueOf(value);
        if ("si".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s)) return Boolean.TRUE;
        if ("no".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) return Boolean.FALSE;
        return null;
    }

    private Integer asInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(value)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Number asNumber(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private Integer clampOrNull(Integer value, int min, int max) {
        return value == null ? null : clamp(value, min, max);
    }
}
