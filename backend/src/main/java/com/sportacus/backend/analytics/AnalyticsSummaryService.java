package com.sportacus.backend.analytics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.sportacus.backend.profile.Profile360Service;
import com.sportacus.backend.security.AdminAccess;

/**
 * Resumen de producto para el equipo (GET /api/admin/analytics/summary). Solo agregados: ningún dato individual.
 * <ul>
 * <li><b>activation48hPct</b>: de quienes se registraron hace ≥ 48 h, % que registró una sesión en sus primeras 48 h.</li>
 * <li><b>d1/d7/d30Pct</b>: retención "rodante": de quienes se registraron hace ≥ N días, % con actividad (evento o
 * sesión) N días o más después del registro.</li>
 * <li><b>sessionsPerActiveUserWeek</b>: sesiones de los últimos 7 días / personas con al menos una sesión en ese
 * lapso.</li>
 * <li><b>avgProfileCompleteness</b>: promedio del % del Perfil 360 (sobre las 300 cuentas más recientes).</li>
 * </ul>
 */
@Service
public class AnalyticsSummaryService {

    private static final int COMPLETENESS_SAMPLE = 300;

    private final JdbcTemplate jdbc;
    private final Profile360Service profile360;
    private final AdminAccess adminAccess;

    public AnalyticsSummaryService(JdbcTemplate jdbc, Profile360Service profile360,
            AdminAccess adminAccess) {
        this.jdbc = jdbc;
        this.profile360 = profile360;
        this.adminAccess = adminAccess;
    }

    public boolean isAdmin(String email) {
        return adminAccess.isAdmin(email);
    }

    public record WorldStat(String key, long enrolled, long active) {
    }

    public record EventCount(String name, long count) {
    }

    public record FunnelStep(String step, long users) {
    }

    public record Summary(long users, long newUsers7d, double activation48hPct, double d1Pct, double d7Pct,
            double d30Pct, double sessionsPerActiveUserWeek, double avgProfileCompleteness, List<WorldStat> worlds,
            List<EventCount> topEvents, List<FunnelStep> funnel) {
    }

    public Summary summary() {
        long users = count("SELECT count(*) FROM app_user");
        long new7 = count("SELECT count(*) FROM app_user WHERE created_at > now() - interval '7 days'");
        double activation = pct("""
                SELECT count(*) FILTER (WHERE EXISTS (SELECT 1 FROM session_log s WHERE s.user_id = u.id
                                                       AND s.created_at <= u.created_at + interval '48 hours')),
                       count(*)
                FROM app_user u WHERE u.created_at <= now() - interval '48 hours'
                """);
        double sessions7 = count("SELECT count(*) FROM session_log WHERE created_at > now() - interval '7 days'");
        double active7 = count(
                "SELECT count(DISTINCT user_id) FROM session_log WHERE created_at > now() - interval '7 days'");
        List<WorldStat> worlds = jdbc.query("""
                SELECT world_key, count(*), count(*) FILTER (WHERE status = 'activo')
                FROM user_world GROUP BY world_key ORDER BY count(*) DESC, world_key
                """, (rs, i) -> new WorldStat(rs.getString(1), rs.getLong(2), rs.getLong(3)));
        List<EventCount> top = jdbc.query("""
                SELECT name, count(*) FROM event_log WHERE occurred_at > now() - interval '30 days'
                GROUP BY name ORDER BY count(*) DESC, name LIMIT 20
                """, (rs, i) -> new EventCount(rs.getString(1), rs.getLong(2)));
        List<FunnelStep> funnel = new ArrayList<>();
        funnel.add(new FunnelStep("registro", users));
        funnel.add(new FunnelStep("chequeo_salud", count("SELECT count(DISTINCT user_id) FROM health_screening")));
        funnel.add(new FunnelStep("perfil_360", count("SELECT count(DISTINCT user_id) FROM profile_answer")));
        funnel.add(new FunnelStep("mundo_inscrito",
                count("SELECT count(DISTINCT user_id) FROM user_world WHERE world_key <> 'base'")));
        funnel.add(new FunnelStep("primera_sesion", count("SELECT count(DISTINCT user_id) FROM session_log")));
        funnel.add(new FunnelStep("tres_sesiones", count("""
                SELECT count(*) FROM (SELECT user_id FROM session_log GROUP BY user_id HAVING count(*) >= 3) t
                """)));
        return new Summary(users, new7, activation, retention(1), retention(7), retention(30),
                active7 == 0 ? 0 : round1(sessions7 / active7), avgCompleteness(), worlds, top, funnel);
    }

    private double retention(int days) {
        return pct("""
                SELECT count(*) FILTER (WHERE
                         EXISTS (SELECT 1 FROM event_log e WHERE e.user_id = u.id
                                 AND e.occurred_at >= u.created_at + make_interval(days => ?))
                      OR EXISTS (SELECT 1 FROM session_log s WHERE s.user_id = u.id
                                 AND s.created_at >= u.created_at + make_interval(days => ?))),
                       count(*)
                FROM app_user u WHERE u.created_at <= now() - make_interval(days => ?)
                """, days, days, days);
    }

    private double avgCompleteness() {
        List<UUID> ids = jdbc.queryForList("SELECT id FROM app_user ORDER BY created_at DESC LIMIT ?", UUID.class,
                COMPLETENESS_SAMPLE);
        if (ids.isEmpty()) return 0;
        double sum = 0;
        int n = 0;
        for (UUID id : ids) {
            try {
                sum += profile360.completeness(id).pct();
                n++;
            } catch (RuntimeException ignored) {
                // una cuenta con datos raros no rompe el resumen
            }
        }
        return n == 0 ? 0 : round1(sum / n);
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private double pct(String sql, Object... args) {
        return jdbc.query(sql, rs -> {
            if (!rs.next()) return 0.0;
            long hit = rs.getLong(1);
            long total = rs.getLong(2);
            return total == 0 ? 0.0 : round1(100.0 * hit / total);
        }, args);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
