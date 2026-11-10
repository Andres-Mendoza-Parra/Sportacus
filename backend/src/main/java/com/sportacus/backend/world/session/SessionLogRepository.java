package com.sportacus.backend.world.session;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.sportacus.backend.common.Json;
import com.sportacus.backend.world.core.LoggedSession;
import com.sportacus.backend.world.core.LoggedSession.LoggedBlock;

/** Tablas session_log y block_log. */
@Repository
public class SessionLogRepository {

    private final JdbcTemplate jdbc;
    private final Json json;

    public SessionLogRepository(JdbcTemplate jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record SessionSummary(UUID id, UUID planSessionId, LocalDate performedOn, String title, Integer durationMin,
            Integer rpe, Integer painBefore, Integer painAfter, Integer painNextDay, Double distanceM,
            Integer moodBefore, Integer moodAfter) {
    }

    public UUID insert(UUID userId, UUID userWorldId, String title, LoggedSession s) {
        return insert(userId, userWorldId, title, s, null);
    }

    /** @param clientId identificador que genera la app para no registrar dos veces la misma sesión */
    public UUID insert(UUID userId, UUID userWorldId, String title, LoggedSession s, UUID clientId) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO session_log (user_id, user_world_id, plan_session_id, title, performed_on, duration_min,
                    rpe, pain_before, pain_after, mood_before, mood_after, distance_m, notes, client_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, UUID.class, userId, userWorldId, s.planSessionId(), title, s.performedOn(), s.durationMin(),
                s.rpe(), s.painBefore(), s.painAfter(), s.moodBefore(), s.moodAfter(), s.distanceM(), s.notes(),
                clientId);
        for (LoggedBlock b : s.blocks()) {
            jdbc.update("""
                    INSERT INTO block_log (session_log_id, plan_block_id, movement_key, set_index, reps, load_kg, rir,
                        hold_s, duration_s, distance_m, pain, completed)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, b.planBlockId(), b.movementKey(), b.setIndex(), b.reps(), b.loadKg(), b.rir(), b.holdS(),
                    b.durationS(), b.distanceM(), b.pain(), b.completed());
        }
        return id;
    }

    public List<SessionSummary> recent(UUID userWorldId, int limit) {
        return jdbc.query("""
                SELECT id, plan_session_id, performed_on, title, duration_min, rpe, pain_before, pain_after,
                       pain_next_day, distance_m, mood_before, mood_after
                FROM session_log WHERE user_world_id = ? ORDER BY performed_on DESC, created_at DESC LIMIT ?
                """, (rs, i) -> new SessionSummary((UUID) rs.getObject(1), (UUID) rs.getObject(2),
                rs.getObject(3, LocalDate.class), rs.getString(4), intOrNull(rs, 5), intOrNull(rs, 6),
                intOrNull(rs, 7), intOrNull(rs, 8), intOrNull(rs, 9),
                rs.getObject(10) == null ? null : rs.getDouble(10), intOrNull(rs, 11), intOrNull(rs, 12)),
                userWorldId, limit);
    }

    public int countSince(UUID userWorldId, LocalDate since) {
        Integer v = jdbc.queryForObject("SELECT count(*) FROM session_log WHERE user_world_id = ? AND performed_on >= ?",
                Integer.class, userWorldId, since);
        return v == null ? 0 : v;
    }

    public int countAll(UUID userId) {
        Integer v = jdbc.queryForObject("SELECT count(*) FROM session_log WHERE user_id = ?", Integer.class, userId);
        return v == null ? 0 : v;
    }

    public int countWorld(UUID userWorldId) {
        Integer v = jdbc.queryForObject("SELECT count(*) FROM session_log WHERE user_world_id = ?", Integer.class,
                userWorldId);
        return v == null ? 0 : v;
    }

    /**
     * Última fecha en que se hizo cada sesión desde {@code since}, indexada por "semana:día" del plan
     * y no por id: si el plan se regenera a mitad de semana (p. ej. tras desbloquear un escalón), lo
     * ya hecho sigue contando.
     */
    public Map<String, LocalDate> doneSince(UUID userWorldId, LocalDate since) {
        Map<String, LocalDate> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT ps.week_index, ps.day_index, max(l.performed_on) FROM session_log l
                JOIN plan_session ps ON ps.id = l.plan_session_id
                WHERE l.user_world_id = ? AND l.performed_on >= ?
                GROUP BY ps.week_index, ps.day_index
                """, rs -> {
            out.put(rs.getInt(1) + ":" + rs.getInt(2), rs.getObject(3, LocalDate.class));
        }, userWorldId, since);
        return out;
    }

    /** Series registradas de un movimiento, de la más reciente a la más antigua. */
    public List<LoggedBlock> blocksOf(UUID userWorldId, String movementKey, int limit) {
        return jdbc.query("""
                SELECT b.plan_block_id, b.movement_key, b.set_index, b.reps, b.load_kg, b.rir, b.hold_s, b.duration_s,
                       b.distance_m, b.pain, b.completed
                FROM block_log b JOIN session_log s ON s.id = b.session_log_id
                WHERE s.user_world_id = ? AND b.movement_key = ?
                ORDER BY s.performed_on DESC, s.created_at DESC, b.set_index LIMIT ?
                """, (rs, i) -> new LoggedBlock((UUID) rs.getObject(1), rs.getString(2), rs.getInt(3),
                intOrNull(rs, 4), rs.getObject(5) == null ? null : rs.getDouble(5), intOrNull(rs, 6),
                intOrNull(rs, 7), intOrNull(rs, 8), rs.getObject(9) == null ? null : rs.getDouble(9),
                intOrNull(rs, 10), rs.getBoolean(11), null, null, null, null), userWorldId, movementKey, limit);
    }

    public Optional<UUID> ownerWorld(UUID sessionLogId) {
        return jdbc.query("SELECT user_world_id FROM session_log WHERE id = ?", (rs, i) -> (UUID) rs.getObject(1),
                sessionLogId).stream().findFirst();
    }

    /** @return true si se guardó (false si ya tenía el dolor del día siguiente) */
    public boolean setNextDayPain(UUID sessionLogId, int pain) {
        return jdbc.update("UPDATE session_log SET pain_next_day = ? WHERE id = ? AND pain_next_day IS NULL", pain,
                sessionLogId) > 0;
    }

    public record PainTarget(UUID userId, UUID userWorldId, LocalDate performedOn, Integer painNextDay) {
    }

    /** Bloquea el registro hasta el final de la transacción (dos envíos simultáneos no duplican efectos). */
    public Optional<PainTarget> lockForPain(UUID sessionLogId) {
        return jdbc.query("""
                SELECT user_id, user_world_id, performed_on, pain_next_day FROM session_log WHERE id = ? FOR UPDATE
                """, (rs, i) -> new PainTarget((UUID) rs.getObject(1), (UUID) rs.getObject(2),
                rs.getObject(3, LocalDate.class), intOrNull(rs, 4)), sessionLogId).stream().findFirst();
    }

    // ── Idempotencia (clientId) ───────────────────────────────

    public record Previous(UUID id, String result) {
    }

    /** Serializa los envíos con el mismo clientId del mismo usuario dentro de la transacción en curso. */
    public void lockClient(UUID userId, UUID clientId) {
        jdbc.queryForList("SELECT 1 FROM pg_advisory_xact_lock(hashtext(?))", Integer.class,
                "session_log:" + userId + ":" + clientId);
    }

    public Optional<Previous> byClient(UUID userId, UUID clientId) {
        return jdbc.query("SELECT id, result::text FROM session_log WHERE user_id = ? AND client_id = ?",
                (rs, i) -> new Previous((UUID) rs.getObject(1), rs.getString(2)), userId, clientId).stream().findFirst();
    }

    public void saveResult(UUID sessionLogId, String resultJson) {
        jdbc.update("UPDATE session_log SET result = ?::jsonb WHERE id = ?", resultJson, sessionLogId);
    }

    // ── Plan dueño de una sesión (aunque ya no sea el activo) ──

    public record PlanSessionRef(UUID userWorldId, UUID planId, String kind, String title) {
    }

    public Optional<PlanSessionRef> planSession(UUID planSessionId) {
        return jdbc.query("""
                SELECT p.user_world_id, p.id, s.kind, s.title FROM plan_session s JOIN plan p ON p.id = s.plan_id
                WHERE s.id = ?
                """, (rs, i) -> new PlanSessionRef((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3),
                rs.getString(4)), planSessionId).stream().findFirst();
    }

    /** Bloques de una sesión del plan, tal como se prescribieron (del plan que la contiene, activo o archivado). */
    public List<com.sportacus.backend.world.plan.PlanRepository.BlockRow> plannedBlocks(UUID planSessionId) {
        return jdbc.query("""
                SELECT id, plan_session_id, order_index, block_type, movement_key, title, prescription::text,
                       progression_key, step_index, notes
                FROM plan_block WHERE plan_session_id = ? ORDER BY order_index
                """, (rs, i) -> new com.sportacus.backend.world.plan.PlanRepository.BlockRow((UUID) rs.getObject(1),
                (UUID) rs.getObject(2), rs.getInt(3), rs.getString(4), rs.getString(5), rs.getString(6),
                json.read(rs.getString(7), com.sportacus.backend.world.core.Prescription.class), rs.getString(8),
                intOrNull(rs, 9), rs.getString(10)), planSessionId);
    }

    /** Sesión anterior a hoy sin dolor de 24 h registrado (para el chequeo del día siguiente en Salud). */
    public Optional<SessionSummary> pendingNextDayCheck(UUID userWorldId, LocalDate today) {
        return recent(userWorldId, 3).stream()
                .filter(s -> s.performedOn().isBefore(today) && !s.performedOn().isBefore(today.minusDays(2))
                        && s.painNextDay() == null)
                .findFirst();
    }

    /** Fechas con actividad (cualquier mundo) para la racha. */
    public List<LocalDate> activeDates(UUID userId, LocalDate since) {
        return jdbc.queryForList("""
                SELECT DISTINCT performed_on FROM session_log WHERE user_id = ? AND performed_on >= ?
                ORDER BY performed_on
                """, LocalDate.class, userId, since);
    }

    private static Integer intOrNull(java.sql.ResultSet rs, int col) throws java.sql.SQLException {
        Object v = rs.getObject(col);
        return v == null ? null : ((Number) v).intValue();
    }

    Json json() {
        return json;
    }
}
