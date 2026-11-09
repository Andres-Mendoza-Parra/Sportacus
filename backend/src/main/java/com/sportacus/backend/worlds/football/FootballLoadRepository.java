package com.sportacus.backend.worlds.football;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.sportacus.backend.worlds.football.LoadMath.DayLoad;

/**
 * Lecturas propias del mundo Fútbol sobre session_log (fecha real de la sesión, performed_on), para que la carga
 * semanal y el ACWR sean correctos aunque una sesión se registre con fecha pasada (metric_entry usa now()).
 */
@Repository
public class FootballLoadRepository {

    private final JdbcTemplate jdbc;

    public FootballLoadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Carga sRPE por día (RPE × minutos) desde {@code since}. */
    public List<DayLoad> dailyLoads(UUID userWorldId, LocalDate since) {
        return jdbc.query("""
                SELECT performed_on, sum(rpe * duration_min) FROM session_log
                WHERE user_world_id = ? AND performed_on >= ? AND rpe IS NOT NULL AND duration_min IS NOT NULL
                GROUP BY performed_on ORDER BY performed_on
                """, (rs, i) -> new DayLoad(rs.getObject(1, LocalDate.class), rs.getDouble(2)), userWorldId, since);
    }

    /** Primer día con una sesión registrada en el mundo (o null). */
    public LocalDate firstSessionDay(UUID userWorldId) {
        return jdbc.queryForObject("SELECT min(performed_on) FROM session_log WHERE user_world_id = ?",
                LocalDate.class, userWorldId);
    }

    /** Sesiones y minutos desde {@code since}. */
    public int[] sessionsAndMinutesSince(UUID userWorldId, LocalDate since) {
        return jdbc.queryForObject("""
                SELECT count(*), COALESCE(sum(duration_min), 0) FROM session_log
                WHERE user_world_id = ? AND performed_on >= ?
                """, (rs, i) -> new int[] { rs.getInt(1), rs.getInt(2) }, userWorldId, since);
    }

    /** Sesiones por tipo (kind del plan) desde {@code since}; las sesiones libres cuentan como "libre". */
    public Map<String, Integer> kindsSince(UUID userWorldId, LocalDate since) {
        Map<String, Integer> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT COALESCE(ps.kind, 'libre'), count(*) FROM session_log l
                LEFT JOIN plan_session ps ON ps.id = l.plan_session_id
                WHERE l.user_world_id = ? AND l.performed_on >= ?
                GROUP BY 1
                """, rs -> {
            out.put(rs.getString(1), rs.getInt(2));
        }, userWorldId, since);
        return out;
    }

    /** Semanas distintas (lunes a domingo) con algún bloque de los movimientos dados completado. */
    public int weeksWithMovements(UUID userWorldId, LocalDate since, List<String> movementKeys) {
        if (movementKeys.isEmpty()) return 0;
        String in = String.join(",", movementKeys.stream().map(k -> "?").toList());
        Object[] args = new Object[2 + movementKeys.size()];
        args[0] = userWorldId;
        args[1] = since;
        for (int i = 0; i < movementKeys.size(); i++) args[2 + i] = movementKeys.get(i);
        Integer v = jdbc.queryForObject("""
                SELECT count(DISTINCT date_trunc('week', l.performed_on)) FROM session_log l
                JOIN block_log b ON b.session_log_id = l.id
                WHERE l.user_world_id = ? AND l.performed_on >= ? AND b.completed AND b.movement_key IN (%s)
                """.formatted(in), Integer.class, args);
        return v == null ? 0 : v;
    }

    /** Partidos registrados (sesiones del plan de tipo partido). */
    public int matchesLogged(UUID userWorldId) {
        Integer v = jdbc.queryForObject("""
                SELECT count(*) FROM session_log l JOIN plan_session ps ON ps.id = l.plan_session_id
                WHERE l.user_world_id = ? AND ps.kind = 'partido'
                """, Integer.class, userWorldId);
        return v == null ? 0 : v;
    }
}
