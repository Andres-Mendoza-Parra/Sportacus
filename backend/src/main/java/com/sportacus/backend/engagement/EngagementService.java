package com.sportacus.backend.engagement;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.sportacus.backend.world.metrics.MetricRepository;
import com.sportacus.backend.world.session.SessionLogRepository;

/**
 * Racha y logros, comunes a todos los mundos. La racha tolera hasta 2 días de descanso entre
 * sesiones (descansar también es parte del plan); los logros pueden ser globales o de un mundo.
 */
@Service
public class EngagementService {

    public static final int STREAK_MAX_GAP_DAYS = 3;

    private final JdbcTemplate jdbc;
    private final SessionLogRepository sessionLogs;

    public EngagementService(JdbcTemplate jdbc, SessionLogRepository sessionLogs) {
        this.jdbc = jdbc;
        this.sessionLogs = sessionLogs;
    }

    public record Streak(int currentDays, int longestDays, LocalDate lastActiveDate) {
    }

    public record Achievement(String key, String worldKey, String title, String icon, OffsetDateTime achievedAt) {
    }

    /** Recalcula la racha a partir de las fechas con actividad. */
    public Streak refreshStreak(UUID userId) {
        LocalDate today = LocalDate.now(MetricRepository.ZONE);
        List<LocalDate> dates = sessionLogs.activeDates(userId, today.minusDays(400));
        int current = 0;
        int longest = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate d : dates) {
            run = (prev == null || d.toEpochDay() - prev.toEpochDay() > STREAK_MAX_GAP_DAYS) ? 1 : run + 1;
            longest = Math.max(longest, run);
            prev = d;
        }
        if (prev != null && today.toEpochDay() - prev.toEpochDay() <= STREAK_MAX_GAP_DAYS) {
            current = run;
        }
        Integer stored = jdbc.queryForList("SELECT longest_days FROM user_streak WHERE user_id = ?", Integer.class, userId)
                .stream().findFirst().orElse(0);
        longest = Math.max(longest, stored);
        jdbc.update("""
                INSERT INTO user_streak (user_id, current_days, longest_days, last_active_date) VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET current_days = EXCLUDED.current_days,
                    longest_days = EXCLUDED.longest_days, last_active_date = EXCLUDED.last_active_date
                """, userId, current, longest, prev);
        return new Streak(current, longest, prev);
    }

    public Streak streak(UUID userId) {
        return jdbc.query("SELECT current_days, longest_days, last_active_date FROM user_streak WHERE user_id = ?",
                (rs, i) -> new Streak(rs.getInt(1), rs.getInt(2), rs.getObject(3, LocalDate.class)), userId)
                .stream().findFirst().orElse(new Streak(0, 0, null));
    }

    /** Otorga un logro si no lo tenía. @return true si es nuevo */
    public boolean grant(UUID userId, String worldKey, String key, String title, String icon) {
        return jdbc.update("""
                INSERT INTO achievement (user_id, key, world_key, title, icon) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id, key) DO NOTHING
                """, userId, key, worldKey, title, icon) > 0;
    }

    public List<Achievement> achievements(UUID userId, String worldKey) {
        return jdbc.query("""
                SELECT key, world_key, title, icon, achieved_at FROM achievement
                WHERE user_id = ? AND (world_key IS NULL OR world_key = ?) ORDER BY achieved_at DESC
                """, (rs, i) -> new Achievement(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getObject(5, OffsetDateTime.class)), userId, worldKey);
    }

    /** Logros genéricos tras una sesión. Devuelve los nuevos. */
    public List<Achievement> genericAfterSession(UUID userId, UUID userWorldId, String worldKey, String worldName,
            Streak streak) {
        List<Achievement> fresh = new ArrayList<>();
        int inWorld = sessionLogs.countWorld(userWorldId);
        int total = sessionLogs.countAll(userId);
        tryGrant(fresh, userId, worldKey, "primera_sesion:" + worldKey, "Primera sesión en " + worldName, "🎉", inWorld >= 1);
        tryGrant(fresh, userId, worldKey, "sesiones_10:" + worldKey, "10 sesiones en " + worldName, "🔟", inWorld >= 10);
        tryGrant(fresh, userId, worldKey, "sesiones_25:" + worldKey, "25 sesiones en " + worldName, "🏅", inWorld >= 25);
        tryGrant(fresh, userId, null, "sesiones_50", "50 sesiones en Sportacus", "🏆", total >= 50);
        tryGrant(fresh, userId, null, "racha_7", "Racha de 7 sesiones", "🔥", streak.currentDays() >= 7);
        tryGrant(fresh, userId, null, "racha_30", "Racha de 30 sesiones", "🌋", streak.currentDays() >= 30);
        return fresh;
    }

    private void tryGrant(List<Achievement> fresh, UUID userId, String worldKey, String key, String title, String icon,
            boolean condition) {
        if (condition && grant(userId, worldKey, key, title, icon)) {
            fresh.add(new Achievement(key, worldKey, title, icon, OffsetDateTime.now()));
        }
    }
}
