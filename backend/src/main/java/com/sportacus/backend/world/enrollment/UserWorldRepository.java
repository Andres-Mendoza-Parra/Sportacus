package com.sportacus.backend.world.enrollment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.common.Json;
import com.sportacus.backend.world.core.EnrollmentResult.GoalSpec;
import com.sportacus.backend.world.core.UserWorldData;

/** Tablas user_world, user_world_goal y user_progression. */
@Repository
public class UserWorldRepository {

    private static final String COLUMNS = """
            id, user_id, world_key, track_key, level, profile::text AS profile, state::text AS state, status,
            enrolled_at, last_active_at
            """;

    private final JdbcTemplate jdbc;
    private final Json json;

    public UserWorldRepository(JdbcTemplate jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private UserWorldData map(ResultSet rs, int i) throws SQLException {
        return new UserWorldData((UUID) rs.getObject("id"), (UUID) rs.getObject("user_id"), rs.getString("world_key"),
                rs.getString("track_key"), rs.getInt("level"), new Answers(json.readMap(rs.getString("profile"))),
                json.readMap(rs.getString("state")), "activo".equals(rs.getString("status")),
                rs.getTimestamp("enrolled_at").toInstant().atOffset(ZoneOffset.UTC),
                rs.getTimestamp("last_active_at").toInstant().atOffset(ZoneOffset.UTC));
    }

    public Optional<UserWorldData> findActive(UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_world WHERE user_id = ? AND status = 'activo'", this::map,
                userId).stream().findFirst();
    }

    public Optional<UserWorldData> find(UUID userId, String worldKey) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_world WHERE user_id = ? AND world_key = ?", this::map,
                userId, worldKey).stream().findFirst();
    }

    public Optional<UserWorldData> byId(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_world WHERE id = ?", this::map, id).stream().findFirst();
    }

    public List<UserWorldData> list(UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_world WHERE user_id = ? ORDER BY last_active_at DESC",
                this::map, userId);
    }

    /** Inscribe (o reinscribe) y deja este mundo como el único activo. */
    public UUID enroll(UUID userId, String worldKey, String trackKey, int level, Map<String, Object> profile,
            Map<String, Object> state) {
        jdbc.update("UPDATE user_world SET status = 'pausado' WHERE user_id = ? AND status = 'activo' AND world_key <> ?",
                userId, worldKey);
        return jdbc.queryForObject("""
                INSERT INTO user_world (user_id, world_key, track_key, level, profile, state, status,
                                        onboarding_completed_at, last_active_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, 'activo', now(), now())
                ON CONFLICT (user_id, world_key) DO UPDATE SET track_key = EXCLUDED.track_key,
                    level = EXCLUDED.level, profile = EXCLUDED.profile, state = EXCLUDED.state, status = 'activo',
                    onboarding_completed_at = now(), last_active_at = now()
                RETURNING id
                """, UUID.class, userId, worldKey, trackKey, level, json.write(profile), json.write(state));
    }

    public void activate(UUID userId, String worldKey) {
        jdbc.update("UPDATE user_world SET status = 'pausado' WHERE user_id = ? AND status = 'activo'", userId);
        jdbc.update("UPDATE user_world SET status = 'activo', last_active_at = now() WHERE user_id = ? AND world_key = ?",
                userId, worldKey);
    }

    public void mergeState(UUID userWorldId, Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) return;
        jdbc.update("UPDATE user_world SET state = state || ?::jsonb WHERE id = ?", json.write(updates), userWorldId);
    }

    public void setLevel(UUID userWorldId, int level) {
        jdbc.update("UPDATE user_world SET level = ? WHERE id = ?", Math.max(1, Math.min(5, level)), userWorldId);
    }

    public void touch(UUID userWorldId) {
        jdbc.update("UPDATE user_world SET last_active_at = now() WHERE id = ?", userWorldId);
    }

    // ── Objetivos ─────────────────────────────────────────────

    public record Goal(UUID id, String goalKey, String label, Map<String, Object> target, LocalDate targetDate,
            String status) {
    }

    public void replaceGoals(UUID userWorldId, List<GoalSpec> goals) {
        jdbc.update("UPDATE user_world_goal SET status = 'abandonada' WHERE user_world_id = ? AND status = 'activa'",
                userWorldId);
        for (GoalSpec g : goals == null ? List.<GoalSpec>of() : goals) {
            jdbc.update("""
                    INSERT INTO user_world_goal (user_world_id, goal_key, label, target, target_date)
                    VALUES (?, ?, ?, ?::jsonb, ?)
                    """, userWorldId, g.goalKey(), g.label(), json.write(g.target() == null ? Map.of() : g.target()),
                    g.targetDate());
        }
    }

    public List<Goal> goals(UUID userWorldId) {
        return jdbc.query("""
                SELECT id, goal_key, label, target::text, target_date, status FROM user_world_goal
                WHERE user_world_id = ? AND status <> 'abandonada' ORDER BY created_at
                """, (rs, i) -> new Goal((UUID) rs.getObject(1), rs.getString(2), rs.getString(3),
                json.readMap(rs.getString(4)), rs.getObject(5, LocalDate.class), rs.getString(6)), userWorldId);
    }

    public void achieveGoal(UUID goalId) {
        jdbc.update("UPDATE user_world_goal SET status = 'lograda', achieved_at = now() WHERE id = ? AND status = 'activa'",
                goalId);
    }

    // ── Progresiones ──────────────────────────────────────────

    public Map<String, Integer> progressionSteps(UUID userWorldId) {
        Map<String, Integer> out = new LinkedHashMap<>();
        jdbc.query("SELECT progression_key, current_step FROM user_progression WHERE user_world_id = ?", rs -> {
            out.put(rs.getString(1), rs.getInt(2));
        }, userWorldId);
        return out;
    }

    public void setProgressionStep(UUID userWorldId, String progressionKey, int step) {
        jdbc.update("""
                INSERT INTO user_progression (user_world_id, progression_key, current_step, best_step, updated_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (user_world_id, progression_key) DO UPDATE SET current_step = EXCLUDED.current_step,
                    best_step = GREATEST(user_progression.best_step, EXCLUDED.current_step), updated_at = now()
                """, userWorldId, progressionKey, step, step);
    }

    public void replaceProgressions(UUID userWorldId, Map<String, Integer> steps) {
        jdbc.update("DELETE FROM user_progression WHERE user_world_id = ?", userWorldId);
        if (steps != null) steps.forEach((k, v) -> setProgressionStep(userWorldId, k, v));
    }
}
