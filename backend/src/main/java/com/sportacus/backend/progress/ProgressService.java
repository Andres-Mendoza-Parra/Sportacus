package com.sportacus.backend.progress;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.progress.ProgressRepository.MetricRow;
import com.sportacus.backend.progress.ProgressRepository.StreakRow;
import com.sportacus.backend.progress.dto.ProgressDtos.AchievementItem;
import com.sportacus.backend.progress.dto.ProgressDtos.ChallengeItem;
import com.sportacus.backend.progress.dto.ProgressDtos.MetricPoint;
import com.sportacus.backend.progress.dto.ProgressDtos.MetricRequest;
import com.sportacus.backend.progress.dto.ProgressDtos.MetricSeries;
import com.sportacus.backend.progress.dto.ProgressDtos.ProgressSummary;
import com.sportacus.backend.progress.dto.ProgressDtos.SessionRequest;
import com.sportacus.backend.progress.dto.ProgressDtos.SessionResult;
import com.sportacus.backend.progress.dto.ProgressDtos.WeekPoint;
import com.sportacus.backend.recommendation.RecommendationRepository;

/**
 * Seguimiento ligero: sesiones cerradas, racha, logros, retos y mediciones.
 * La "racha" cuenta sesiones seguidas tolerando hasta 2 días de descanso entre una y otra,
 * porque una rutina real incluye días libres.
 */
@Service
public class ProgressService {

    /** Días máximos entre dos sesiones para que la racha continúe (2 de descanso + el día de la sesión). */
    private static final int STREAK_MAX_GAP_DAYS = 3;
    private static final int WEEKS_SHOWN = 8;
    private static final int TEST_INTERVAL_DAYS = 28;
    private static final Pattern METRIC_KEY = Pattern.compile("^[a-z0-9_]{2,60}$");
    private static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("dd/MM");

    private record Stats(int total, int longestStreak, int currentStreak, int weekSessions, int streakWeeks,
            int testsDone) {
    }

    private record AchievementDef(String key, String title, String description, String icon, String type,
            java.util.function.ToIntFunction<Stats> progress, int target) {
    }

    private static final List<AchievementDef> ACHIEVEMENTS = List.of(
            new AchievementDef("first_session", "Primer paso", "Completa tu primera sesión", "🌱", "habito",
                    Stats::total, 1),
            new AchievementDef("sessions_10", "Constante", "Completa 10 sesiones", "🔥", "consistencia",
                    Stats::total, 10),
            new AchievementDef("sessions_25", "Imparable", "Completa 25 sesiones", "⚡", "consistencia",
                    Stats::total, 25),
            new AchievementDef("sessions_50", "Atleta", "Completa 50 sesiones", "🏅", "consistencia",
                    Stats::total, 50),
            new AchievementDef("streak_3", "En racha", "3 sesiones seguidas", "📈", "habito",
                    Stats::longestStreak, 3),
            new AchievementDef("streak_7", "Racha de fuego", "7 sesiones seguidas", "🚀", "habito",
                    Stats::longestStreak, 7),
            new AchievementDef("week_3", "Semana completa", "3 sesiones en una misma semana", "📅", "consistencia",
                    Stats::weekSessions, 3),
            new AchievementDef("weeks_4", "Mes constante", "4 semanas seguidas entrenando", "🗓️", "consistencia",
                    Stats::streakWeeks, 4),
            new AchievementDef("first_test", "Medido y probado", "Haz tu primera evaluación física", "📏",
                    "rendimiento", Stats::testsDone, 1));

    private final ProgressRepository repository;
    private final RecommendationRepository recommendationRepository;

    public ProgressService(ProgressRepository repository, RecommendationRepository recommendationRepository) {
        this.repository = repository;
        this.recommendationRepository = recommendationRepository;
    }

    // ───────────────────────── Cierre de sesión ─────────────────────────

    public SessionResult closeSession(UUID userId, SessionRequest request) {
        String feel = request == null ? null : request.feel();
        Integer rpe = switch (feel == null ? "" : feel) {
            case "easy" -> 3;
            case "right" -> 6;
            case "hard" -> 9;
            default -> null;
        };
        Integer duration = request == null || request.durationMin() == null ? null
                : Math.max(1, Math.min(600, request.durationMin()));
        String notes = request == null || request.activityName() == null ? null
                : "ruta:" + request.activityName().substring(0, Math.min(60, request.activityName().length()))
                        + (feel == null ? "" : ";feel:" + feel);

        int biasBefore = recommendationRepository.difficultyBias(userId);
        repository.insertSession(userId, duration, rpe, notes);
        updateStreak(userId);

        Stats stats = stats(userId);
        List<AchievementItem> fresh = awardNewAchievements(userId, stats);

        int biasAfter = recommendationRepository.difficultyBias(userId);
        String adjustment = biasAfter > biasBefore || (biasAfter > 0 && biasBefore == 0) ? "harder"
                : (biasAfter < biasBefore || (biasAfter < 0 && biasBefore == 0) ? "easier" : null);

        return new SessionResult(stats.currentStreak(), stats.longestStreak(), stats.total(), stats.weekSessions(),
                fresh, adjustment);
    }

    private void updateStreak(UUID userId) {
        LocalDate today = LocalDate.now();
        StreakRow row = repository.findStreak(userId);
        int current;
        if (row.lastActivity() == null) {
            current = 1;
        } else if (row.lastActivity().equals(today)) {
            current = Math.max(1, row.current());
        } else if (ChronoUnit.DAYS.between(row.lastActivity(), today) <= STREAK_MAX_GAP_DAYS) {
            current = row.current() + 1;
        } else {
            current = 1;
        }
        repository.saveStreak(userId, current, Math.max(current, row.longest()), today);
    }

    // ───────────────────────── Mediciones ─────────────────────────

    public void saveMetric(UUID userId, MetricRequest request) {
        if (request == null || request.metricKey() == null || !METRIC_KEY.matcher(request.metricKey()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "metricKey debe usar solo minúsculas, números y guion bajo");
        }
        if (request.value() == null || request.value().isNaN() || request.value().isInfinite()
                || Math.abs(request.value()) > 1_000_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value no es un número válido");
        }
        String context = request.context() == null ? null
                : request.context().substring(0, Math.min(120, request.context().length()));
        repository.saveMetric(userId, request.metricKey(), request.value(), request.unit(), context);
        // Una evaluación física puede desbloquear un logro.
        awardNewAchievements(userId, stats(userId));
    }

    // ───────────────────────── Resumen ─────────────────────────

    public ProgressSummary summary(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Stats stats = stats(userId);

        List<LocalDate> dates = repository.sessionDatesSince(userId, thisMonday.minusWeeks(WEEKS_SHOWN - 1L));
        List<WeekPoint> weeks = new ArrayList<>();
        for (int i = WEEKS_SHOWN - 1; i >= 0; i--) {
            LocalDate start = thisMonday.minusWeeks(i);
            LocalDate end = start.plusDays(7);
            int count = (int) dates.stream().filter(d -> !d.isBefore(start) && d.isBefore(end)).count();
            weeks.add(new WeekPoint(start.format(WEEK_LABEL), count));
        }

        List<MetricRow> rows = repository.metrics(userId, 30);
        Map<String, List<MetricRow>> byKey = new LinkedHashMap<>();
        for (MetricRow r : rows) {
            byKey.computeIfAbsent(r.key(), k -> new ArrayList<>()).add(r);
        }
        List<MetricSeries> series = byKey.entrySet().stream().map(e -> new MetricSeries(e.getKey(),
                e.getValue().get(e.getValue().size() - 1).unit(),
                e.getValue().stream().map(r -> new MetricPoint(r.date().toString(), r.value())).toList())).toList();

        LocalDate lastTest = rows.stream().filter(r -> r.key().startsWith("test_")).map(MetricRow::date)
                .max(LocalDate::compareTo).orElse(null);
        boolean testsDue = lastTest != null && ChronoUnit.DAYS.between(lastTest, today) >= TEST_INTERVAL_DAYS;

        Map<String, OffsetDateTime> achieved = repository.achievedMilestones(userId);
        List<AchievementItem> achievements = ACHIEVEMENTS.stream().map(def -> {
            OffsetDateTime when = achieved.get(def.key());
            int current = Math.min(def.target(), def.progress().applyAsInt(stats));
            return new AchievementItem(def.key(), def.title(), def.description(), def.icon(),
                    when != null || current >= def.target(), when == null ? null : when.toString(), current,
                    def.target());
        }).toList();

        List<ChallengeItem> challenges = List.of(
                challenge("challenge_7", "Reto 7 días", "4 sesiones en los últimos 7 días", dates, today.minusDays(6),
                        4),
                challenge("challenge_30", "Reto 30 días", "12 sesiones en los últimos 30 días",
                        repository.sessionDatesSince(userId, today.minusDays(29)), today.minusDays(29), 12));

        return new ProgressSummary(effectiveStreak(repository.findStreak(userId)), repository.findStreak(userId).longest(),
                stats.total(), stats.weekSessions(), stats.streakWeeks(), repository.averageRpe(userId, 10), weeks,
                repository.recentSessions(userId, 10), series, achievements, challenges,
                lastTest == null ? null : lastTest.toString(), testsDue);
    }

    private ChallengeItem challenge(String key, String title, String description, List<LocalDate> dates,
            LocalDate since, int target) {
        int count = (int) dates.stream().filter(d -> !d.isBefore(since)).count();
        return new ChallengeItem(key, title, description, Math.min(count, target), target, count >= target);
    }

    // ───────────────────────── Cálculos ─────────────────────────

    /** La racha mostrada vale 0 si ya pasó demasiado tiempo desde la última sesión. */
    private int effectiveStreak(StreakRow row) {
        if (row.lastActivity() == null
                || ChronoUnit.DAYS.between(row.lastActivity(), LocalDate.now()) > STREAK_MAX_GAP_DAYS) {
            return 0;
        }
        return row.current();
    }

    private Stats stats(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<LocalDate> dates = repository.sessionDatesSince(userId, thisMonday.minusWeeks(WEEKS_SHOWN - 1L));

        int weekSessions = (int) dates.stream().filter(d -> !d.isBefore(thisMonday)).count();

        // Semanas consecutivas con al menos una sesión (la semana en curso vacía aún no rompe la cuenta).
        int streakWeeks = 0;
        for (int i = 0; i < WEEKS_SHOWN; i++) {
            LocalDate start = thisMonday.minusWeeks(i);
            LocalDate end = start.plusDays(7);
            boolean any = dates.stream().anyMatch(d -> !d.isBefore(start) && d.isBefore(end));
            if (any) {
                streakWeeks++;
            } else if (i > 0) {
                break;
            }
        }

        StreakRow streak = repository.findStreak(userId);
        int testsDone = (int) repository.metrics(userId, 1).stream().filter(r -> r.key().startsWith("test_")).count();
        return new Stats(repository.totalSessions(userId), streak.longest(), effectiveStreak(streak), weekSessions,
                streakWeeks, testsDone);
    }

    private List<AchievementItem> awardNewAchievements(UUID userId, Stats stats) {
        Map<String, OffsetDateTime> already = repository.achievedMilestones(userId);
        List<AchievementItem> fresh = new ArrayList<>();
        for (AchievementDef def : ACHIEVEMENTS) {
            if (already.containsKey(def.key()) || def.progress().applyAsInt(stats) < def.target()) {
                continue;
            }
            repository.markAchieved(userId, def.key(), def.type(), def.description());
            fresh.add(new AchievementItem(def.key(), def.title(), def.description(), def.icon(), true,
                    OffsetDateTime.now().toString(), def.target(), def.target()));
        }
        return fresh;
    }
}
