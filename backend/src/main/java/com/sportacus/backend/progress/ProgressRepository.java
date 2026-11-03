package com.sportacus.backend.progress;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.sportacus.backend.progress.dto.ProgressDtos.SessionItem;

/**
 * Acceso a las tablas de seguimiento que el esquema ya trae: workout_session, user_streak,
 * progress_record y milestone. No requiere migraciones.
 */
@Repository
public class ProgressRepository {

    public record StreakRow(int current, int longest, LocalDate lastActivity) {
    }

    public record MetricRow(String key, String unit, LocalDate date, double value) {
    }

    private static final String TRACK_PREFIX = "track_";

    private final JdbcTemplate jdbcTemplate;

    public ProgressRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ───────────────────────── Sesiones ─────────────────────────

    public void insertSession(UUID userId, Integer durationMin, Integer rpe, String notes) {
        jdbcTemplate.update("""
                INSERT INTO sportacus.workout_session
                    (user_id, session_date, session_type, duration_min, rpe_1_10, completed, notes)
                VALUES (?, CURRENT_DATE, 'mixto', ?, ?, TRUE, ?)
                """, userId, durationMin, rpe, notes);
    }

    public int totalSessions(UUID userId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sportacus.workout_session WHERE user_id = ? AND completed = TRUE",
                Integer.class, userId);
        return n == null ? 0 : n;
    }

    /** Fechas (una por sesión) desde la fecha indicada. */
    public List<LocalDate> sessionDatesSince(UUID userId, LocalDate since) {
        return jdbcTemplate.query("""
                SELECT session_date FROM sportacus.workout_session
                WHERE user_id = ? AND completed = TRUE AND session_date >= ?
                """, (rs, rowNum) -> rs.getDate(1).toLocalDate(), userId, Date.valueOf(since));
    }

    public List<SessionItem> recentSessions(UUID userId, int limit) {
        return jdbcTemplate.query("""
                SELECT session_date, session_type, duration_min, rpe_1_10
                FROM sportacus.workout_session
                WHERE user_id = ? AND completed = TRUE
                ORDER BY session_date DESC, created_at DESC LIMIT ?
                """, (rs, rowNum) -> new SessionItem(rs.getDate(1).toString(), rs.getString(2),
                rs.getObject(3) != null ? rs.getInt(3) : null, rs.getObject(4) != null ? rs.getInt(4) : null),
                userId, limit);
    }

    public Double averageRpe(UUID userId, int lastN) {
        List<Integer> rpes = jdbcTemplate.query("""
                SELECT rpe_1_10 FROM sportacus.workout_session
                WHERE user_id = ? AND rpe_1_10 IS NOT NULL
                ORDER BY session_date DESC, created_at DESC LIMIT ?
                """, (rs, rowNum) -> rs.getInt(1), userId, lastN);
        return rpes.isEmpty() ? null : rpes.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    // ───────────────────────── Racha ─────────────────────────

    public StreakRow findStreak(UUID userId) {
        List<StreakRow> rows = jdbcTemplate.query("""
                SELECT current_streak_days, longest_streak_days, last_activity_date
                FROM sportacus.user_streak WHERE user_id = ?
                """, (rs, rowNum) -> new StreakRow(rs.getInt(1), rs.getInt(2),
                rs.getDate(3) == null ? null : rs.getDate(3).toLocalDate()), userId);
        return rows.isEmpty() ? new StreakRow(0, 0, null) : rows.get(0);
    }

    public void saveStreak(UUID userId, int current, int longest, LocalDate last) {
        int updated = jdbcTemplate.update("""
                UPDATE sportacus.user_streak
                SET current_streak_days = ?, longest_streak_days = ?, last_activity_date = ?
                WHERE user_id = ?
                """, current, longest, Date.valueOf(last), userId);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO sportacus.user_streak
                        (user_id, current_streak_days, longest_streak_days, last_activity_date)
                    VALUES (?, ?, ?, ?)
                    """, userId, current, longest, Date.valueOf(last));
        }
    }

    // ───────────────────────── Métricas ─────────────────────────

    /** Guarda una medición; si ya hay una del mismo día y contexto, la reemplaza. */
    @Transactional
    public void saveMetric(UUID userId, String publicKey, double value, String unit, String context) {
        // progress_record también guarda el nombre y las respuestas de cuestionarios: se aísla con prefijo.
        String key = TRACK_PREFIX + publicKey;
        jdbcTemplate.update("""
                DELETE FROM sportacus.progress_record
                WHERE user_id = ? AND metric_key = ? AND recorded_at = CURRENT_DATE
                  AND COALESCE(context, '') = COALESCE(?, '')
                """, userId, key, context);
        jdbcTemplate.update("""
                INSERT INTO sportacus.progress_record (user_id, metric_key, value, unit, recorded_at, context)
                VALUES (?, ?, ?, ?, CURRENT_DATE, ?)
                """, userId, key, value, unit, context);
    }

    /** Últimas mediciones de cada métrica (máx. 'perKey' por clave), ordenadas por fecha ascendente. */
    public List<MetricRow> metrics(UUID userId, int perKey) {
        return jdbcTemplate.query("""
                SELECT metric_key, unit, recorded_at, value FROM (
                    SELECT metric_key, unit, recorded_at, value,
                           ROW_NUMBER() OVER (PARTITION BY metric_key ORDER BY recorded_at DESC) AS rn
                    FROM sportacus.progress_record WHERE user_id = ? AND metric_key LIKE 'track\\_%'
                ) t WHERE rn <= ? ORDER BY metric_key, recorded_at
                """, (rs, rowNum) -> new MetricRow(rs.getString(1).substring(TRACK_PREFIX.length()),
                rs.getString(2), rs.getDate(3).toLocalDate(), rs.getDouble(4)), userId, perKey);
    }

    // ───────────────────────── Logros ─────────────────────────

    /** nombre del logro → fecha en que se consiguió. */
    public Map<String, OffsetDateTime> achievedMilestones(UUID userId) {
        Map<String, OffsetDateTime> out = new HashMap<>();
        jdbcTemplate.query("""
                SELECT name, achieved_at FROM sportacus.milestone WHERE user_id = ? AND is_achieved = TRUE
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> out.put(rs.getString(1),
                rs.getObject(2, OffsetDateTime.class)), userId);
        return out;
    }

    public void markAchieved(UUID userId, String key, String type, String description) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sportacus.milestone WHERE user_id = ? AND name = ?", Integer.class, userId,
                key);
        if (exists != null && exists > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO sportacus.milestone (user_id, name, type, description, achieved_at, is_achieved)
                VALUES (?, ?, ?, ?, NOW(), TRUE)
                """, userId, key, type, description);
    }
}
