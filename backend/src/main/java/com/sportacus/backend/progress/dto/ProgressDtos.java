package com.sportacus.backend.progress.dto;

import java.util.List;

/** Contratos de /api/progress. */
public final class ProgressDtos {

    private ProgressDtos() {
    }

    /** Cierre de una sesión de entrenamiento. feel: "easy" | "right" | "hard". */
    public record SessionRequest(Integer durationMin, String feel, Integer completedCount, Integer totalCount,
            String activityName) {
    }

    /** Medición puntual (peso, score, resultado de una prueba física…). */
    public record MetricRequest(String metricKey, Double value, String unit, String context) {
    }

    public record AchievementItem(String key, String title, String description, String icon, boolean achieved,
            String achievedAt, int current, int target) {
    }

    public record ChallengeItem(String key, String title, String description, int current, int target,
            boolean completed) {
    }

    public record WeekPoint(String label, int sessions) {
    }

    public record SessionItem(String date, String type, Integer durationMin, Integer rpe) {
    }

    public record MetricPoint(String date, double value) {
    }

    public record MetricSeries(String key, String unit, List<MetricPoint> points) {
    }

    public record ProgressSummary(
            int currentStreak,
            int longestStreak,
            int totalSessions,
            int sessionsThisWeek,
            int streakWeeks,
            Double avgRpe,
            List<WeekPoint> weeks,
            List<SessionItem> recent,
            List<MetricSeries> series,
            List<AchievementItem> achievements,
            List<ChallengeItem> challenges,
            String lastTestDate,
            boolean testsDue) {
    }

    /** Respuesta al cerrar una sesión; adjustment = "harder" | "easier" | null. */
    public record SessionResult(
            int currentStreak,
            int longestStreak,
            int totalSessions,
            int sessionsThisWeek,
            List<AchievementItem> newAchievements,
            String adjustment) {
    }
}
