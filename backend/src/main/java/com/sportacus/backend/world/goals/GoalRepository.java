package com.sportacus.backend.world.goals;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.sportacus.backend.common.Json;
import com.sportacus.backend.world.goals.GoalProgress.Sample;
import com.sportacus.backend.world.goals.GoalProgress.SessionFact;

/** Metas (user_world_goal) y los datos con que se mide su avance. */
@Repository
public class GoalRepository {

    private final JdbcTemplate jdbc;
    private final Json json;

    public GoalRepository(JdbcTemplate jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record GoalRow(UUID id, UUID userWorldId, UUID userId, String worldKey, String goalKey, String label,
            Map<String, Object> target, LocalDate targetDate, String status, OffsetDateTime createdAt,
            OffsetDateTime achievedAt, Double baseline, String achievedBy) {
    }

    private static final String SELECT = """
            SELECT g.id, g.user_world_id, w.user_id, w.world_key, g.goal_key, g.label, g.target::text, g.target_date,
                   g.status, g.created_at, g.achieved_at, g.baseline, g.achieved_by
            FROM user_world_goal g JOIN user_world w ON w.id = g.user_world_id
            """;

    private GoalRow map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new GoalRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), rs.getString(4),
                rs.getString(5), rs.getString(6), json.readMap(rs.getString(7)), rs.getObject(8, LocalDate.class),
                rs.getString(9), rs.getObject(10, OffsetDateTime.class), rs.getObject(11, OffsetDateTime.class),
                rs.getObject(12) == null ? null : rs.getDouble(12), rs.getString(13));
    }

    /** Metas vigentes (activas y logradas) de un mundo; las abandonadas no se muestran. */
    public List<GoalRow> byWorld(UUID userWorldId) {
        return jdbc.query(SELECT + " WHERE g.user_world_id = ? AND g.status <> 'abandonada' ORDER BY g.created_at",
                (rs, i) -> map(rs), userWorldId);
    }

    public Optional<GoalRow> byId(UUID goalId) {
        return jdbc.query(SELECT + " WHERE g.id = ?", (rs, i) -> map(rs), goalId).stream().findFirst();
    }

    /** @return true si pasó de activa a lograda */
    public boolean achieve(UUID goalId, String by) {
        return jdbc.update("""
                UPDATE user_world_goal SET status = 'lograda', achieved_at = now(), achieved_by = ?
                WHERE id = ? AND status = 'activa'
                """, by, goalId) > 0;
    }

    public boolean reopen(UUID goalId) {
        return jdbc.update("""
                UPDATE user_world_goal SET status = 'activa', achieved_at = NULL, achieved_by = NULL
                WHERE id = ? AND status = 'lograda'
                """, goalId) > 0;
    }

    public void setBaseline(UUID goalId, double baseline) {
        jdbc.update("UPDATE user_world_goal SET baseline = ? WHERE id = ? AND baseline IS NULL", baseline, goalId);
    }

    // ── Datos para medir ──────────────────────────────────────

    public int bestStep(UUID userWorldId, String progressionKey) {
        return jdbc.queryForList("""
                SELECT best_step FROM user_progression WHERE user_world_id = ? AND progression_key = ?
                """, Integer.class, userWorldId, progressionKey).stream().findFirst().orElse(0);
    }

    public int sumSteps(UUID userWorldId) {
        Integer v = jdbc.queryForObject("SELECT COALESCE(sum(current_step), 0) FROM user_progression WHERE user_world_id = ?",
                Integer.class, userWorldId);
        return v == null ? 0 : v;
    }

    public List<Sample> samples(UUID userWorldId, String metricKey) {
        return jdbc.query("""
                SELECT value, recorded_at, context->>'movement' FROM metric_entry
                WHERE user_world_id = ? AND metric_key = ? ORDER BY recorded_at
                """, (rs, i) -> new Sample(rs.getDouble(1), rs.getObject(2, OffsetDateTime.class), rs.getString(3)),
                userWorldId, metricKey);
    }

    public List<SessionFact> sessions(UUID userWorldId, LocalDate since) {
        return jdbc.query("""
                SELECT s.performed_on, s.duration_min, s.distance_m,
                       max(b.duration_s) FILTER (WHERE b.completed),
                       max(b.distance_m) FILTER (WHERE b.completed),
                       string_agg(DISTINCT b.movement_key, ',') FILTER (WHERE b.completed AND b.movement_key IS NOT NULL)
                FROM session_log s LEFT JOIN block_log b ON b.session_log_id = s.id
                WHERE s.user_world_id = ? AND s.performed_on >= ?
                GROUP BY s.id, s.performed_on, s.duration_min, s.distance_m
                ORDER BY s.performed_on, s.id
                """, (rs, i) -> {
            Set<String> moves = new LinkedHashSet<>();
            String raw = rs.getString(6);
            if (raw != null) for (String m : raw.split(",")) if (!m.isBlank()) moves.add(m);
            return new SessionFact(rs.getObject(1, LocalDate.class),
                    rs.getObject(2) == null ? null : rs.getInt(2),
                    rs.getObject(3) == null ? null : rs.getDouble(3),
                    rs.getObject(4) == null ? null : rs.getInt(4),
                    rs.getObject(5) == null ? null : rs.getDouble(5), moves);
        }, userWorldId, since);
    }
}
