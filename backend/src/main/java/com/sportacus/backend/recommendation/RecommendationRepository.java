package com.sportacus.backend.recommendation;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationRepository {

    public static final String ENGINE_VERSION = "v2";

    private static final String METRIC_LIKE = "ejercicio_like";
    private static final String METRIC_DISLIKE = "ejercicio_dislike";
    private static final String METRIC_SWAP = "ejercicio_swap";
    private static final String METRIC_DISCARD = "ruta_descartada";
    /** Checklist de la rutina vigente: context = id de exercise_recommendation. Se borra al generar otra rutina. */
    private static final String METRIC_DONE = "checklist_done";
    private static final int DISCARD_MEMORY_DAYS = 45;

    private final JdbcTemplate jdbcTemplate;

    public RecommendationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ───────────────────────── Contexto del usuario ─────────────────────────

    /**
     * Todo lo que el motor sabe del usuario. Los campos pueden ser null/vacíos: el motor puntúa
     * "neutro" lo que no se conoce, y gana precisión a medida que se completan las plantillas.
     */
    public record UserContext(
            String primaryGoal,
            UUID goalId,
            int experienceLevel,
            List<String> environments,
            List<String> ownedEquipmentNames,
            boolean hasActiveInjury,
            Integer age,
            Double bmi,
            Integer sleepQuality,
            Integer stressLevel,
            Double sleepHours,
            Integer yearsActive,
            List<String> disciplines,
            String timePerSession,
            Integer daysPerWeek,
            List<String> preferredDays,
            String homeSpace,
            boolean outdoorSafe,
            Integer gymDistanceMin,
            String budgetMembership,
            String socialPreference,
            List<String> conditions,
            List<String> injuryAreas,
            String injurySeverity,
            String timeHorizon,
            List<String> secondaryGoals,
            Integer motivation,
            Double seatedHours,
            boolean hasExperienceData) {
    }

    public UserContext loadUserContext(UUID userId) {
        List<Map<String, Object>> goalRows = jdbcTemplate.queryForList("""
                SELECT id, primary_goal, time_horizon, secondary_goals, motivation_1_10
                FROM sportacus.user_goal
                WHERE user_id = ? AND is_active = TRUE ORDER BY created_at DESC LIMIT 1
                """, userId);
        Map<String, Object> goal = goalRows.isEmpty() ? Map.of() : goalRows.get(0);
        String primaryGoal = (String) goal.get("primary_goal");
        UUID goalId = (UUID) goal.get("id");

        List<Map<String, Object>> profileRows = jdbcTemplate.queryForList("""
                SELECT birth_date, weight_kg, height_cm, sleep_hours_avg, sleep_quality_1_5, stress_level_1_5,
                       experience_level, years_active, current_disciplines, seated_hours_day
                FROM sportacus.user_profile WHERE user_id = ?
                """, userId);
        Map<String, Object> profile = profileRows.isEmpty() ? Map.of() : profileRows.get(0);
        int declaredLevel = profile.get("experience_level") instanceof Number n ? n.intValue() : 0;
        // El esfuerzo percibido de las últimas sesiones sube o baja el nivel efectivo (0..5).
        int experienceLevel = Math.max(0, Math.min(5, declaredLevel + difficultyBias(userId)));

        Integer age = null;
        if (profile.get("birth_date") instanceof java.sql.Date birth) {
            age = java.time.Period.between(birth.toLocalDate(), java.time.LocalDate.now()).getYears();
        }
        Double bmi = null;
        if (profile.get("weight_kg") instanceof Number w && profile.get("height_cm") instanceof Number h
                && h.doubleValue() > 0) {
            double meters = h.doubleValue() / 100.0;
            bmi = w.doubleValue() / (meters * meters);
        }

        List<Map<String, Object>> resourceRows = jdbcTemplate.queryForList("""
                SELECT available_environments, home_space_size, time_per_session, budget_membership,
                       gym_distance_min, outdoor_safe, social_preference
                FROM sportacus.user_resources WHERE user_id = ?
                """, userId);
        Map<String, Object> resources = resourceRows.isEmpty() ? Map.of() : resourceRows.get(0);

        List<String> equipmentNames = jdbcTemplate.query("""
                SELECT eq.name AS name
                FROM sportacus.user_equipment ue
                JOIN sportacus.user_resources ur ON ur.id = ue.user_resources_id
                JOIN sportacus.equipment eq ON eq.id = ue.equipment_id
                WHERE ur.user_id = ?
                """, (rs, rowNum) -> rs.getString("name"), userId);

        List<Map<String, Object>> injuries = jdbcTemplate.queryForList(
                "SELECT location, severity FROM sportacus.injury WHERE user_id = ? AND current_status = 'activa'",
                userId);
        List<String> injuryAreas = new ArrayList<>();
        String worstSeverity = null;
        for (Map<String, Object> injury : injuries) {
            injuryAreas.addAll(areasFor(String.valueOf(injury.get("location"))));
            String severity = (String) injury.get("severity");
            if (severityRank(severity) > severityRank(worstSeverity)) {
                worstSeverity = severity;
            }
        }

        List<String> conditions = jdbcTemplate.query("SELECT type FROM sportacus.condition WHERE user_id = ?",
                (rs, rowNum) -> rs.getString("type"), userId);

        Map<String, String> prefs = new HashMap<>();
        jdbcTemplate.query("""
                SELECT metric_key, context FROM sportacus.progress_record
                WHERE user_id = ? AND metric_key LIKE 'pref%' ORDER BY recorded_at, id
                """, rs -> {
            String key = rs.getString("metric_key");
            if (key != null && key.startsWith("pref_")) {
                prefs.put(key.substring(5), rs.getString("context"));
            }
        }, userId);

        Integer daysPerWeek = null;
        try {
            if (prefs.get("q_days_per_week") != null) daysPerWeek = Integer.parseInt(prefs.get("q_days_per_week"));
        } catch (NumberFormatException ignored) {
            // preferencia corrupta: se ignora
        }
        List<String> preferredDays = prefs.get("q_preferred_days") == null || prefs.get("q_preferred_days").isBlank()
                ? List.of() : List.of(prefs.get("q_preferred_days").split(","));

        return new UserContext(primaryGoal, goalId, experienceLevel,
                toStringList(resources.get("available_environments")), equipmentNames, !injuries.isEmpty(),
                age, bmi,
                profile.get("sleep_quality_1_5") instanceof Number n ? n.intValue() : null,
                profile.get("stress_level_1_5") instanceof Number n ? n.intValue() : null,
                profile.get("sleep_hours_avg") instanceof Number n ? n.doubleValue() : null,
                profile.get("years_active") instanceof Number n ? n.intValue() : null,
                toStringList(profile.get("current_disciplines")),
                (String) resources.get("time_per_session"), daysPerWeek, preferredDays,
                (String) resources.get("home_space_size"),
                !Boolean.FALSE.equals(resources.get("outdoor_safe")),
                resources.get("gym_distance_min") instanceof Number n ? n.intValue() : null,
                (String) resources.get("budget_membership"), (String) resources.get("social_preference"),
                conditions, injuryAreas, worstSeverity,
                (String) goal.get("time_horizon"), toStringList(goal.get("secondary_goals")),
                goal.get("motivation_1_10") instanceof Number n ? n.intValue() : null,
                profile.get("seated_hours_day") instanceof Number n ? n.doubleValue() : null,
                !profileRows.isEmpty() && profile.get("experience_level") != null);
    }

    private int severityRank(String severity) {
        return severity == null ? 0 : switch (severity) {
            case "grave" -> 3;
            case "moderada" -> 2;
            default -> 1;
        };
    }

    /** Zonas del cuerpo a evitar según el tipo de lesión registrado en el cuestionario. */
    private List<String> areasFor(String injuryLocation) {
        return switch (injuryLocation) {
            case "lesion_articular" -> List.of("hombro", "rodilla", "cadera", "tobillo", "codo", "muneca");
            case "lesion_espalda" -> List.of("lumbar", "espalda", "cervical", "columna");
            case "lesion_muscular" -> List.of();
            default -> List.of(injuryLocation.toLowerCase());
        };
    }

    // ───────────────────────── Retroalimentación ─────────────────────────

    public record Feedback(Set<UUID> liked, Set<UUID> disliked, Map<UUID, Integer> swaps,
            Map<UUID, Integer> discardedActivities, Map<UUID, Integer> likesByActivity,
            Map<UUID, Integer> dislikesByActivity) {
    }

    public Feedback loadFeedback(UUID userId) {
        Set<UUID> liked = new HashSet<>();
        Set<UUID> disliked = new HashSet<>();
        Map<UUID, Integer> swaps = new HashMap<>();
        jdbcTemplate.query("""
                SELECT metric_key, context FROM sportacus.progress_record
                WHERE user_id = ? AND metric_key IN (?, ?, ?)
                """, rs -> {
            UUID id = parseUuid(rs.getString("context"));
            if (id == null) return;
            switch (rs.getString("metric_key")) {
                case METRIC_LIKE -> liked.add(id);
                case METRIC_DISLIKE -> disliked.add(id);
                default -> swaps.merge(id, 1, Integer::sum);
            }
        }, userId, METRIC_LIKE, METRIC_DISLIKE, METRIC_SWAP);

        Map<UUID, Integer> discarded = new HashMap<>();
        jdbcTemplate.query("""
                SELECT context FROM sportacus.progress_record
                WHERE user_id = ? AND metric_key = ? AND recorded_at >= CURRENT_DATE - ?::int
                """, rs -> {
            UUID id = parseUuid(rs.getString("context"));
            if (id != null) discarded.merge(id, 1, Integer::sum);
        }, userId, METRIC_DISCARD, DISCARD_MEMORY_DAYS);

        return new Feedback(liked, disliked, swaps, discarded,
                countByActivity(liked), countByActivity(disliked));
    }

    private Map<UUID, Integer> countByActivity(Set<UUID> exerciseIds) {
        Map<UUID, Integer> counts = new HashMap<>();
        if (exerciseIds.isEmpty()) {
            return counts;
        }
        jdbcTemplate.query("SELECT activity_id FROM sportacus.exercise WHERE id = ANY(?)",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("uuid", exerciseIds.toArray())),
                rs -> {
                    counts.merge((UUID) rs.getObject("activity_id"), 1, Integer::sum);
                });
        return counts;
    }

    /** "like" | "dislike" | null. */
    public String reactionOf(UUID userId, UUID exerciseId) {
        List<String> metrics = jdbcTemplate.query("""
                SELECT metric_key FROM sportacus.progress_record
                WHERE user_id = ? AND metric_key IN (?, ?) AND context = ? ORDER BY recorded_at DESC, id DESC LIMIT 1
                """, (rs, rowNum) -> rs.getString("metric_key"), userId, METRIC_LIKE, METRIC_DISLIKE,
                exerciseId.toString());
        if (metrics.isEmpty()) return null;
        return METRIC_LIKE.equals(metrics.get(0)) ? "like" : "dislike";
    }

    public void setReaction(UUID userId, UUID exerciseId, String reaction) {
        jdbcTemplate.update("""
                DELETE FROM sportacus.progress_record
                WHERE user_id = ? AND metric_key IN (?, ?) AND context = ?
                """, userId, METRIC_LIKE, METRIC_DISLIKE, exerciseId.toString());
        insertSignal(userId, "like".equals(reaction) ? METRIC_LIKE : METRIC_DISLIKE, exerciseId);
    }

    public void addSwap(UUID userId, UUID exerciseId) {
        insertSignal(userId, METRIC_SWAP, exerciseId);
    }

    public void addDiscard(UUID userId, UUID activityId) {
        insertSignal(userId, METRIC_DISCARD, activityId);
    }

    private void insertSignal(UUID userId, String metricKey, UUID id) {
        jdbcTemplate.update("""
                INSERT INTO sportacus.progress_record (user_id, metric_key, value, recorded_at, context)
                VALUES (?, ?, 1, CURRENT_DATE, ?)
                """, userId, metricKey, id.toString());
    }

    private UUID parseUuid(String text) {
        try {
            return text == null ? null : UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ───────────────────────── Catálogo ─────────────────────────

    public record ActivityRow(
            UUID id, String name, String description, String categoryName, String environment,
            String jointImpact, String intensityLevel, String equipmentRequired,
            int minEntryLevel, Integer typicalSessionMin, List<String> compatibleGoals,
            Integer ageRangeMin, Integer ageRangeMax, List<String> commonRestrictions) {
    }

    public List<ActivityRow> listActiveActivities() {
        String sql = """
                SELECT a.id, a.name, a.description, c.name AS category_name, a.environment, a.joint_impact,
                       a.intensity_level, a.equipment_required, a.min_entry_level, a.typical_session_min,
                       a.compatible_goals, a.age_range_min, a.age_range_max, a.common_restrictions
                FROM sportacus.activity a
                JOIN sportacus.activity_category c ON c.id = a.category_id
                WHERE a.is_active = TRUE
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ActivityRow(
                (UUID) rs.getObject("id"), rs.getString("name"), rs.getString("description"),
                rs.getString("category_name"), rs.getString("environment"), rs.getString("joint_impact"),
                rs.getString("intensity_level"), rs.getString("equipment_required"),
                rs.getInt("min_entry_level"),
                rs.getObject("typical_session_min") != null ? rs.getInt("typical_session_min") : null,
                toStringList(rs.getObject("compatible_goals")),
                rs.getObject("age_range_min") != null ? rs.getInt("age_range_min") : null,
                rs.getObject("age_range_max") != null ? rs.getInt("age_range_max") : null,
                toStringList(rs.getObject("common_restrictions"))));
    }

    public record ExerciseRow(
            UUID id, String name, Integer difficultyLevel, String muscleGroupPrimaryName, String movementPattern,
            UUID muscleGroupId, String contraindications, String equipmentNeeded, UUID activityId,
            String categoryName) {
    }

    private static final String EXERCISE_SELECT = """
            SELECT e.id, e.name, e.difficulty_level, mg.name AS muscle_group_name, e.movement_pattern,
                   e.muscle_group_primary, e.contraindications, e.equipment_needed, e.activity_id,
                   c.name AS category_name
            FROM sportacus.exercise e
            JOIN sportacus.activity a ON a.id = e.activity_id
            JOIN sportacus.activity_category c ON c.id = a.category_id
            LEFT JOIN sportacus.muscle_group mg ON mg.id = e.muscle_group_primary
            """;

    private ExerciseRow mapExercise(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ExerciseRow(
                (UUID) rs.getObject("id"), rs.getString("name"),
                rs.getObject("difficulty_level") != null ? rs.getInt("difficulty_level") : null,
                rs.getString("muscle_group_name"), rs.getString("movement_pattern"),
                (UUID) rs.getObject("muscle_group_primary"), rs.getString("contraindications"),
                rs.getString("equipment_needed"), (UUID) rs.getObject("activity_id"),
                rs.getString("category_name"));
    }

    public List<ExerciseRow> listExercisesForActivity(UUID activityId) {
        return jdbcTemplate.query(EXERCISE_SELECT
                + " WHERE e.activity_id = ? AND e.is_active = TRUE ORDER BY e.difficulty_level ASC NULLS LAST, e.name",
                (rs, rowNum) -> mapExercise(rs), activityId);
    }

    /** Ejercicios de otras actividades de la misma categoría: sirven para dar variedad al regenerar. */
    public List<ExerciseRow> listSiblingExercises(String categoryName, UUID excludeActivityId) {
        return jdbcTemplate.query(EXERCISE_SELECT
                + " WHERE c.name = ? AND e.activity_id <> ? AND e.is_active = TRUE ORDER BY e.name",
                (rs, rowNum) -> mapExercise(rs), categoryName, excludeActivityId);
    }

    /** Candidatos para sustituir un ejercicio: misma categoría y mismo grupo muscular o patrón de movimiento. */
    public List<ExerciseRow> listSwapCandidates(String categoryName, UUID muscleGroupId, String movementPattern) {
        return jdbcTemplate.query(EXERCISE_SELECT
                + " WHERE c.name = ? AND e.is_active = TRUE AND (e.muscle_group_primary = ? OR e.movement_pattern = ?)",
                (rs, rowNum) -> mapExercise(rs), categoryName, muscleGroupId, movementPattern);
    }

    public int countActiveActivities() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sportacus.activity WHERE is_active = TRUE", Integer.class);
        return count == null ? 0 : count;
    }

    // ───────────────────────── Ruta vigente ─────────────────────────

    public record ExistingRoute(UUID routeId, UUID activityId, String activityName, String description,
            String categoryName, String environment, String intensityLevel, Integer typicalSessionMin,
            int score, String rationale, String primaryGoal, OffsetDateTime createdAt, String engineVersion,
            List<String> dayLabels) {
    }

    /** Última ruta vigente del usuario (se mantiene hasta que un cambio de perfil o el usuario la regenera). */
    public Optional<ExistingRoute> findCurrentRoute(UUID userId) {
        String sql = """
                SELECT rr.id AS route_id, rr.score, rr.rationale, rr.created_at, rr.engine_version, rr.day_labels,
                       a.id AS activity_id, a.name AS activity_name,
                       a.description, c.name AS category_name, a.environment, a.intensity_level, a.typical_session_min,
                       ug.primary_goal
                FROM sportacus.route_recommendation rr
                JOIN sportacus.activity a ON a.id = rr.activity_id
                JOIN sportacus.activity_category c ON c.id = a.category_id
                LEFT JOIN sportacus.user_goal ug ON ug.id = rr.goal_id
                WHERE rr.user_id = ? AND rr.rank_position = 1 AND rr.engine_version = ?
                ORDER BY rr.created_at DESC LIMIT 1
                """;
        List<ExistingRoute> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new ExistingRoute(
                (UUID) rs.getObject("route_id"), (UUID) rs.getObject("activity_id"), rs.getString("activity_name"),
                rs.getString("description"), rs.getString("category_name"), rs.getString("environment"),
                rs.getString("intensity_level"),
                rs.getObject("typical_session_min") != null ? rs.getInt("typical_session_min") : null,
                (int) Math.round(rs.getDouble("score")), rs.getString("rationale"), rs.getString("primary_goal"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getString("engine_version"),
                toStringList(rs.getObject("day_labels"))),
                userId, ENGINE_VERSION);
        return rows.stream().findFirst();
    }

    /** Sobrescribe los días (etiquetas) de una ruta: usado al generar y al editar en el Taller. */
    public void updateDayLabels(UUID routeId, List<String> dayLabels) {
        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE sportacus.route_recommendation SET day_labels = ? WHERE id = ?")) {
                ps.setArray(1, conn.createArrayOf("text", dayLabels.toArray()));
                ps.setObject(2, routeId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public record RecommendedExerciseRow(
            UUID exerciseRecommendationId, UUID exerciseId, String name, Integer sets, Integer reps,
            Integer durationSec, Integer restSec, String muscleGroupName, Integer difficultyLevel,
            int dayIndex) {
    }

    public List<RecommendedExerciseRow> listExerciseRecommendations(UUID routeId) {
        String sql = """
                SELECT er.id, er.exercise_id, e.name, er.sets, er.reps, er.duration_sec, er.rest_sec,
                       mg.name AS muscle_group_name, e.difficulty_level, er.day_index
                FROM sportacus.exercise_recommendation er
                JOIN sportacus.exercise e ON e.id = er.exercise_id
                LEFT JOIN sportacus.muscle_group mg ON mg.id = e.muscle_group_primary
                WHERE er.route_recommendation_id = ?
                ORDER BY er.day_index, er.order_index
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new RecommendedExerciseRow(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("exercise_id"), rs.getString("name"),
                rs.getObject("sets") != null ? rs.getInt("sets") : null,
                rs.getObject("reps") != null ? rs.getInt("reps") : null,
                rs.getObject("duration_sec") != null ? rs.getInt("duration_sec") : null,
                rs.getObject("rest_sec") != null ? rs.getInt("rest_sec") : null,
                rs.getString("muscle_group_name"),
                rs.getObject("difficulty_level") != null ? rs.getInt("difficulty_level") : null,
                rs.getInt("day_index")), routeId);
    }

    public boolean isExerciseLogged(UUID exerciseRecommendationId) {
        String sql = """
                SELECT COUNT(*) FROM sportacus.session_exercise se
                JOIN sportacus.workout_session ws ON ws.id = se.session_id
                WHERE se.notes = ?
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, loggedMarker(exerciseRecommendationId));
        return count != null && count > 0;
    }

    private String loggedMarker(UUID exerciseRecommendationId) {
        return "cuestionario_rutina:" + exerciseRecommendationId;
    }

    // ───────────────────────── Guardado de una ruta nueva ─────────────────────────

    public void deleteExistingRoutes(UUID userId) {
        jdbcTemplate.update("DELETE FROM sportacus.route_recommendation WHERE user_id = ?", userId);
        // Rutina nueva = checklist nuevo (y no quedan registros huérfanos de ids ya borrados).
        jdbcTemplate.update("DELETE FROM sportacus.progress_record WHERE user_id = ? AND metric_key = ?",
                userId, METRIC_DONE);
    }

    // ───────────────────────── Checklist de la rutina ─────────────────────────

    /** Ids de exercise_recommendation marcados como hechos. */
    public Set<UUID> doneRecommendationIds(UUID userId) {
        Set<UUID> done = new HashSet<>();
        jdbcTemplate.query("SELECT context FROM sportacus.progress_record WHERE user_id = ? AND metric_key = ?",
                rs -> {
                    UUID id = parseUuid(rs.getString("context"));
                    if (id != null) done.add(id);
                }, userId, METRIC_DONE);
        return done;
    }

    public void setDone(UUID userId, UUID exerciseRecommendationId, boolean done) {
        clearDone(userId, exerciseRecommendationId);
        if (done) {
            insertSignal(userId, METRIC_DONE, exerciseRecommendationId);
        }
    }

    public void clearDone(UUID userId, UUID exerciseRecommendationId) {
        jdbcTemplate.update("DELETE FROM sportacus.progress_record WHERE user_id = ? AND metric_key = ? AND context = ?",
                userId, METRIC_DONE, exerciseRecommendationId.toString());
    }

    public UUID insertRoute(UUID userId, UUID goalId, UUID activityId, int score, String rationale,
            Map<String, Object> scoreBreakdown, int rankPosition, List<String> dayLabels) {
        UUID id = UUID.randomUUID();
        String breakdownJson = toJson(scoreBreakdown);
        jdbcTemplate.update("""
                INSERT INTO sportacus.route_recommendation
                    (id, user_id, goal_id, activity_id, score, rank_position, rationale, score_breakdown,
                     engine_version, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'sugerida')
                """, id, userId, goalId, activityId, storableScore(score), rankPosition, rationale, breakdownJson,
                ENGINE_VERSION);
        updateDayLabels(id, dayLabels);
        return id;
    }

    public void insertExerciseRecommendation(UUID routeId, UUID exerciseId, int orderIndex, int score,
            Integer sets, Integer reps, Integer durationSec, Integer restSec, int dayIndex) {
        jdbcTemplate.update("""
                INSERT INTO sportacus.exercise_recommendation
                    (route_recommendation_id, exercise_id, order_index, score, sets, reps, duration_sec, rest_sec,
                     day_index)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, routeId, exerciseId, orderIndex, storableScore(score), sets, reps, durationSec, restSec,
                dayIndex);
    }

    /** Actualiza series/reps/duración y el día de una recomendación ya existente (edición del Taller). */
    public void updateExerciseRecommendation(UUID exerciseRecommendationId, Integer sets, Integer reps,
            Integer durationSec, int dayIndex) {
        jdbcTemplate.update("""
                UPDATE sportacus.exercise_recommendation
                SET sets = ?, reps = ?, duration_sec = ?, day_index = ?
                WHERE id = ?
                """, sets, reps, durationSec, dayIndex, exerciseRecommendationId);
    }

    /** Quita un ejercicio de la rutina por completo (el Taller permite esto; antes no existía). */
    public void deleteExerciseRecommendation(UUID exerciseRecommendationId) {
        jdbcTemplate.update("DELETE FROM sportacus.session_exercise WHERE notes = ?",
                loggedMarker(exerciseRecommendationId));
        jdbcTemplate.update("DELETE FROM sportacus.exercise_recommendation WHERE id = ?", exerciseRecommendationId);
    }

    /** Borra los ejercicios de un día y recorre hacia atrás el índice de los días posteriores. */
    public void removeDayExercises(UUID routeId, int dayIndex) {
        jdbcTemplate.update("""
                DELETE FROM sportacus.exercise_recommendation
                WHERE route_recommendation_id = ? AND day_index = ?
                """, routeId, dayIndex);
        jdbcTemplate.update("""
                UPDATE sportacus.exercise_recommendation SET day_index = day_index - 1
                WHERE route_recommendation_id = ? AND day_index > ?
                """, routeId, dayIndex);
    }

    /** Las columnas score son NUMERIC(6,4): el máximo almacenable es 99.9999. */
    private double storableScore(int score) {
        return Math.max(0, Math.min(99.99, score));
    }

    // ───────────────────────── Sustitución de un ejercicio ─────────────────────────

    public record RecommendationRef(UUID id, UUID routeId, UUID exerciseId, UUID activityId, int orderIndex,
            Integer sets, Integer reps, Integer durationSec, Integer restSec, UUID muscleGroupId,
            String movementPattern, String categoryName, int dayIndex) {
    }

    public Optional<RecommendationRef> findRecommendationRef(UUID userId, UUID exerciseRecommendationId) {
        String sql = """
                SELECT er.id, er.route_recommendation_id, er.exercise_id, e.activity_id, er.order_index, er.sets,
                       er.reps, er.duration_sec, er.rest_sec, e.muscle_group_primary, e.movement_pattern,
                       c.name AS category_name, er.day_index
                FROM sportacus.exercise_recommendation er
                JOIN sportacus.route_recommendation rr ON rr.id = er.route_recommendation_id
                JOIN sportacus.exercise e ON e.id = er.exercise_id
                JOIN sportacus.activity a ON a.id = e.activity_id
                JOIN sportacus.activity_category c ON c.id = a.category_id
                WHERE rr.user_id = ? AND er.id = ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new RecommendationRef(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("route_recommendation_id"),
                (UUID) rs.getObject("exercise_id"), (UUID) rs.getObject("activity_id"), rs.getInt("order_index"),
                rs.getObject("sets") != null ? rs.getInt("sets") : null,
                rs.getObject("reps") != null ? rs.getInt("reps") : null,
                rs.getObject("duration_sec") != null ? rs.getInt("duration_sec") : null,
                rs.getObject("rest_sec") != null ? rs.getInt("rest_sec") : null,
                (UUID) rs.getObject("muscle_group_primary"), rs.getString("movement_pattern"),
                rs.getString("category_name"), rs.getInt("day_index")), userId, exerciseRecommendationId)
                .stream().findFirst();
    }

    public Set<UUID> routeExerciseIds(UUID routeId) {
        return new HashSet<>(jdbcTemplate.query(
                "SELECT exercise_id FROM sportacus.exercise_recommendation WHERE route_recommendation_id = ?",
                (rs, rowNum) -> (UUID) rs.getObject("exercise_id"), routeId));
    }

    /** Cambia el ejercicio de una recomendación conservando su posición y su id. */
    public void replaceExercise(UUID exerciseRecommendationId, UUID newExerciseId, int score) {
        jdbcTemplate.update("UPDATE sportacus.exercise_recommendation SET exercise_id = ?, score = ? WHERE id = ?",
                newExerciseId, storableScore(score), exerciseRecommendationId);
        // El registro de "añadido a mi rutina" era del ejercicio anterior.
        jdbcTemplate.update("DELETE FROM sportacus.session_exercise WHERE notes = ?",
                loggedMarker(exerciseRecommendationId));
    }

    // ───────────────────────── Detalle de un ejercicio recomendado ─────────────────────────

    public record ExerciseDetailRow(
            UUID exerciseRecommendationId, UUID exerciseId, String name, String description,
            Integer sets, Integer reps, Integer durationSec, Integer restSec,
            String coachingCues, String contraindications, String difficultyLevel,
            String muscleGroupPrimaryName, List<UUID> musclesSecondaryIds, String environment,
            String categoryName, String equipmentNeeded, String videoUrl) {
    }

    public Optional<ExerciseDetailRow> findExerciseDetail(UUID userId, UUID exerciseRecommendationId) {
        String sql = """
                SELECT er.id AS er_id, e.id AS exercise_id, e.name, e.description, er.sets, er.reps,
                       er.duration_sec, er.rest_sec, e.coaching_cues, e.contraindications, e.difficulty_level,
                       mg.name AS muscle_group_name, e.muscle_groups_secondary, a.environment, c.name AS category_name,
                       e.equipment_needed, e.video_url
                FROM sportacus.exercise_recommendation er
                JOIN sportacus.route_recommendation rr ON rr.id = er.route_recommendation_id
                JOIN sportacus.exercise e ON e.id = er.exercise_id
                JOIN sportacus.activity a ON a.id = e.activity_id
                JOIN sportacus.activity_category c ON c.id = a.category_id
                LEFT JOIN sportacus.muscle_group mg ON mg.id = e.muscle_group_primary
                WHERE rr.user_id = ? AND er.id = ?
                """;
        List<ExerciseDetailRow> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new ExerciseDetailRow(
                (UUID) rs.getObject("er_id"), (UUID) rs.getObject("exercise_id"), rs.getString("name"),
                rs.getString("description"),
                rs.getObject("sets") != null ? rs.getInt("sets") : null,
                rs.getObject("reps") != null ? rs.getInt("reps") : null,
                rs.getObject("duration_sec") != null ? rs.getInt("duration_sec") : null,
                rs.getObject("rest_sec") != null ? rs.getInt("rest_sec") : null,
                rs.getString("coaching_cues"), rs.getString("contraindications"),
                rs.getObject("difficulty_level") != null ? String.valueOf(rs.getInt("difficulty_level")) : null,
                rs.getString("muscle_group_name"), toUuidList(rs.getObject("muscle_groups_secondary")),
                rs.getString("environment"), rs.getString("category_name"),
                rs.getString("equipment_needed"), rs.getString("video_url")), userId, exerciseRecommendationId);
        return rows.stream().findFirst();
    }

    public List<String> muscleGroupNames(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT name FROM sportacus.muscle_group WHERE id = ANY(?)";
        return jdbcTemplate.query(sql, ps -> ps.setArray(1, ps.getConnection().createArrayOf("uuid", ids.toArray())),
                (rs, rowNum) -> rs.getString("name"));
    }

    // ───────────────────────── "Añadir a mi rutina" (registrar sesión) ─────────────────────────

    public void logExercise(UUID userId, UUID exerciseRecommendationId, UUID exerciseId, String sessionType,
            Integer sets, Integer reps, Integer durationSec, Integer restSec) {
        String marker = loggedMarker(exerciseRecommendationId);
        Integer already = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sportacus.session_exercise WHERE notes = ?", Integer.class, marker);
        if (already != null && already > 0) {
            return; // ya estaba registrado, no duplicar
        }

        List<UUID> todaySession = jdbcTemplate.query("""
                SELECT id FROM sportacus.workout_session
                WHERE user_id = ? AND session_date = CURRENT_DATE AND session_type = ?
                ORDER BY created_at DESC LIMIT 1
                """, (rs, rowNum) -> (UUID) rs.getObject("id"), userId, sessionType);

        UUID sessionId;
        if (!todaySession.isEmpty()) {
            sessionId = todaySession.get(0);
        } else {
            sessionId = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO sportacus.workout_session (id, user_id, session_date, session_type, completed)
                    VALUES (?, ?, CURRENT_DATE, ?, TRUE)
                    """, sessionId, userId, sessionType);
        }

        jdbcTemplate.update("""
                INSERT INTO sportacus.session_exercise
                    (session_id, exercise_id, order_index, sets, reps, duration_sec, rest_sec, notes)
                VALUES (?, ?, 0, ?, ?, ?, ?, ?)
                """, sessionId, exerciseId, sets, reps, durationSec, restSec, marker);
    }

    // ───────────────────────── Adaptación por esfuerzo percibido ─────────────────────────

    /**
     * Ajuste de nivel según el esfuerzo (RPE) de las últimas 3 sesiones cerradas:
     * -1 si en promedio fueron muy duras (≥ 8), +1 si fueron muy fáciles (≤ 4), 0 si no hay datos suficientes.
     */
    public int difficultyBias(UUID userId) {
        List<Integer> rpes = jdbcTemplate.query("""
                SELECT rpe_1_10 FROM sportacus.workout_session
                WHERE user_id = ? AND rpe_1_10 IS NOT NULL
                ORDER BY session_date DESC, created_at DESC LIMIT 3
                """, (rs, rowNum) -> rs.getInt(1), userId);
        if (rpes.size() < 2) {
            return 0;
        }
        double avg = rpes.stream().mapToInt(Integer::intValue).average().orElse(6);
        return avg >= 8 ? -1 : (avg <= 4 ? 1 : 0);
    }

    // ───────────────────────── Desglose del score ─────────────────────────

    /** Puntos por factor con los que se calculó la afinidad de una ruta (score_breakdown). */
    public Map<String, Integer> findBreakdown(UUID routeId) {
        Map<String, Integer> out = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT j.key, j.value FROM sportacus.route_recommendation rr,
                       jsonb_each_text(COALESCE(rr.score_breakdown, '{}'::jsonb)) AS j
                WHERE rr.id = ?
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    try {
                        out.put(rs.getString(1), (int) Math.round(Double.parseDouble(rs.getString(2))));
                    } catch (NumberFormatException ignored) {
                        // valores no numéricos no aportan puntos
                    }
                }, routeId);
        return out;
    }

    // ───────────────────────── Biblioteca de ejercicios ─────────────────────────

    public record LibraryRow(UUID id, String name, String muscleGroup, String equipment, Integer difficulty,
            String category, String environment, boolean favorite) {
    }

    public List<LibraryRow> searchExercises(UUID userId, UUID activityId, String query, String muscle,
            String equipment, Integer difficulty, boolean favoritesOnly, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.id, e.name, mg.name AS muscle_name, e.equipment_needed, e.difficulty_level,
                       c.name AS category_name, a.environment, (fe.user_id IS NOT NULL) AS is_favorite
                FROM sportacus.exercise e
                JOIN sportacus.activity a ON a.id = e.activity_id
                JOIN sportacus.activity_category c ON c.id = a.category_id
                LEFT JOIN sportacus.muscle_group mg ON mg.id = e.muscle_group_primary
                LEFT JOIN sportacus.favorite_exercise fe ON fe.exercise_id = e.id AND fe.user_id = ?
                WHERE e.is_active = TRUE AND e.activity_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.add(activityId);
        if (query != null && !query.isBlank()) {
            sql.append(" AND e.name ILIKE ?");
            params.add("%" + query.trim() + "%");
        }
        if (muscle != null && !muscle.isBlank()) {
            sql.append(" AND mg.name = ?");
            params.add(muscle);
        }
        if (equipment != null && !equipment.isBlank()) {
            sql.append(" AND e.equipment_needed ILIKE ?");
            params.add("%" + equipment.trim() + "%");
        }
        if (difficulty != null) {
            sql.append(" AND e.difficulty_level = ?");
            params.add(difficulty);
        }
        if (favoritesOnly) {
            sql.append(" AND fe.user_id IS NOT NULL");
        }
        sql.append(" ORDER BY is_favorite DESC, e.name LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new LibraryRow(
                (UUID) rs.getObject("id"), rs.getString("name"), rs.getString("muscle_name"),
                rs.getString("equipment_needed"),
                rs.getObject("difficulty_level") != null ? rs.getInt("difficulty_level") : null,
                rs.getString("category_name"), rs.getString("environment"), rs.getBoolean("is_favorite")),
                params.toArray());
    }

    // ───────────────────────── Favoritos ─────────────────────────

    public boolean isFavorite(UUID userId, UUID exerciseId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sportacus.favorite_exercise WHERE user_id = ? AND exercise_id = ?",
                Integer.class, userId, exerciseId);
        return n != null && n > 0;
    }

    public void addFavorite(UUID userId, UUID exerciseId) {
        jdbcTemplate.update("""
                INSERT INTO sportacus.favorite_exercise (user_id, exercise_id) VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """, userId, exerciseId);
    }

    public void removeFavorite(UUID userId, UUID exerciseId) {
        jdbcTemplate.update("DELETE FROM sportacus.favorite_exercise WHERE user_id = ? AND exercise_id = ?",
                userId, exerciseId);
    }

    public List<String> listMuscleNamesInUse(UUID activityId) {
        return jdbcTemplate.query("""
                SELECT DISTINCT mg.name FROM sportacus.exercise e
                JOIN sportacus.muscle_group mg ON mg.id = e.muscle_group_primary
                WHERE e.is_active = TRUE AND e.activity_id = ? ORDER BY mg.name
                """, (rs, rowNum) -> rs.getString(1), activityId);
    }

    public Optional<ExerciseDetailRow> findExerciseDetailByExerciseId(UUID exerciseId) {
        String sql = """
                SELECT e.id AS exercise_id, e.name, e.description, e.coaching_cues, e.contraindications,
                       e.difficulty_level, mg.name AS muscle_group_name, e.muscle_groups_secondary,
                       a.environment, c.name AS category_name, e.equipment_needed, e.video_url
                FROM sportacus.exercise e
                JOIN sportacus.activity a ON a.id = e.activity_id
                JOIN sportacus.activity_category c ON c.id = a.category_id
                LEFT JOIN sportacus.muscle_group mg ON mg.id = e.muscle_group_primary
                WHERE e.id = ? AND e.is_active = TRUE
                """;
        List<ExerciseDetailRow> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new ExerciseDetailRow(
                null, (UUID) rs.getObject("exercise_id"), rs.getString("name"), rs.getString("description"),
                null, null, null, null, rs.getString("coaching_cues"), rs.getString("contraindications"),
                rs.getObject("difficulty_level") != null ? String.valueOf(rs.getInt("difficulty_level")) : null,
                rs.getString("muscle_group_name"), toUuidList(rs.getObject("muscle_groups_secondary")),
                rs.getString("environment"), rs.getString("category_name"),
                rs.getString("equipment_needed"), rs.getString("video_url")), exerciseId);
        return rows.stream().findFirst();
    }

    /** ¿Ya está el ejercicio en la ruta indicada? */
    public boolean routeContainsExercise(UUID routeId, UUID exerciseId) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sportacus.exercise_recommendation
                WHERE route_recommendation_id = ? AND exercise_id = ?
                """, Integer.class, routeId, exerciseId);
        return n != null && n > 0;
    }

    /**
     * Añade un ejercicio al final de la ruta, en el día indicado (el llamador resuelve el día:
     * explícito cuando viene del Taller, balanceado cuando viene de "añadir desde Biblioteca").
     */
    public void appendExerciseToRoute(UUID routeId, UUID exerciseId, int score, Integer sets, Integer reps,
            Integer durationSec, Integer restSec, int dayIndex) {
        Integer max = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(order_index), -1) FROM sportacus.exercise_recommendation
                WHERE route_recommendation_id = ?
                """, Integer.class, routeId);
        insertExerciseRecommendation(routeId, exerciseId, (max == null ? -1 : max) + 1, score, sets, reps,
                durationSec, restSec, dayIndex);
    }

    /** Cuántos ejercicios tiene ya la ruta (para balancear el día al añadir desde la Biblioteca). */
    public int countRouteExercises(UUID routeId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sportacus.exercise_recommendation WHERE route_recommendation_id = ?",
                Integer.class, routeId);
        return n == null ? 0 : n;
    }

    // ───────────────────────── Helpers ─────────────────────────

    /**
     * Serializa un mapa plano (claves texto, valores numéricos) a JSON a mano, para no
     * depender de ObjectMapper. Solo se usa para score_breakdown, que siempre es un
     * Map&lt;String, Integer&gt; simple.
     */
    private String toJson(Map<String, Object> flatMap) {
        if (flatMap == null || flatMap.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : flatMap.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append('"').append(entry.getKey().replace("\"", "\\\"")).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number) {
                sb.append(value);
            } else {
                sb.append('"').append(String.valueOf(value).replace("\"", "\\\"")).append('"');
            }
        }
        sb.append("}");
        return sb.toString();
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

    private List<UUID> toUuidList(Object pgArray) {
        if (pgArray == null) {
            return List.of();
        }
        try {
            Object[] values = (Object[]) ((Array) pgArray).getArray();
            List<UUID> result = new ArrayList<>();
            for (Object v : values) {
                result.add((UUID) v);
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }
}
