package com.sportacus.backend.dashboard;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas de lectura para el dashboard. Se usan vía JdbcTemplate (en lugar de mapear una entidad JPA por
 * tabla) porque solo necesitamos lecturas puntuales sobre varias tablas del esquema.
 */
@Repository
public class DashboardQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public DashboardQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Carga de un ejercicio de la rutina vigente: lo necesario para estimar énfasis muscular y tiempos. */
    public record ExerciseLoad(UUID exerciseRecommendationId, Integer sets, Integer reps, Integer durationSec,
            Integer restSec, String muscleGroup, String category) {
    }

    /** Ejercicios de una ruta con su grupo muscular primario y la categoría de su disciplina. */
    public List<ExerciseLoad> routeLoads(UUID routeId) {
        String sql = """
                SELECT er.id, er.sets, er.reps, er.duration_sec, er.rest_sec,
                       mg.name AS muscle_group, cat.name AS category
                FROM sportacus.exercise_recommendation er
                JOIN sportacus.exercise e ON e.id = er.exercise_id
                LEFT JOIN sportacus.muscle_group mg ON mg.id = e.muscle_group_primary
                JOIN sportacus.activity a ON a.id = e.activity_id
                JOIN sportacus.activity_category cat ON cat.id = a.category_id
                WHERE er.route_recommendation_id = ?
                ORDER BY er.order_index
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ExerciseLoad(
                (UUID) rs.getObject("id"),
                rs.getObject("sets") != null ? rs.getInt("sets") : null,
                rs.getObject("reps") != null ? rs.getInt("reps") : null,
                rs.getObject("duration_sec") != null ? rs.getInt("duration_sec") : null,
                rs.getObject("rest_sec") != null ? rs.getInt("rest_sec") : null,
                rs.getString("muscle_group"), rs.getString("category")), routeId);
    }

    /** Datos del perfil que no están en el contexto del motor (hidratación, comidas, pasos, actividad laboral). */
    public record ProfileExtras(Double waterLiters, Integer mealsPerDay, Integer dailySteps, String workActivity) {
    }

    public ProfileExtras profileExtras(UUID userId) {
        List<Map<String, Object>> nutrition = jdbcTemplate.queryForList(
                "SELECT daily_water_liters, meal_count_per_day FROM sportacus.nutrition_profile WHERE user_id = ?",
                userId);
        List<Map<String, Object>> profile = jdbcTemplate.queryForList(
                "SELECT daily_steps_approx, work_activity_type FROM sportacus.user_profile WHERE user_id = ?",
                userId);
        Map<String, Object> n = nutrition.isEmpty() ? Map.of() : nutrition.get(0);
        Map<String, Object> p = profile.isEmpty() ? Map.of() : profile.get(0);
        return new ProfileExtras(
                n.get("daily_water_liters") instanceof Number w ? w.doubleValue() : null,
                n.get("meal_count_per_day") instanceof Number m ? m.intValue() : null,
                p.get("daily_steps_approx") instanceof Number s ? s.intValue() : null,
                (String) p.get("work_activity_type"));
    }
}
