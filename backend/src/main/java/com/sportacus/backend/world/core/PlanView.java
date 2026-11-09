package com.sportacus.backend.world.core;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Plan activo tal como se muestra (y como lo leen los mundos para armar su dashboard). */
public record PlanView(UUID id, String title, String summary, int weeks, int currentWeek, LocalDate startsOn,
        LocalDate weekStart, Double score, List<Factor> rationale, List<SessionView> sessions) {

    /** Sesiones de la semana en curso del plan. */
    public List<SessionView> currentWeekSessions() {
        return sessions.stream().filter(s -> s.weekIndex() == currentWeek).toList();
    }

    /** Primera sesión de la semana en curso que aún no se hizo (o null). */
    public SessionView nextSession() {
        return currentWeekSessions().stream().filter(s -> !s.done()).findFirst().orElse(null);
    }

    public long doneThisWeek() {
        return currentWeekSessions().stream().filter(SessionView::done).count();
    }

    public record SessionView(UUID id, int weekIndex, int dayIndex, Integer weekday, String title, String focus,
            String kind, Integer estMinutes, String notes, boolean done, LocalDate lastDoneOn, List<BlockView> blocks) {
    }

    public record BlockView(UUID id, int orderIndex, String blockType, String movementKey, String movementName,
            String title, Prescription prescription, String progressionKey, Integer stepIndex, String notes,
            String imageUrl) {
    }
}
