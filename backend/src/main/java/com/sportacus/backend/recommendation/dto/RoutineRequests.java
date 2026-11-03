package com.sportacus.backend.recommendation.dto;

import java.util.UUID;

/** Cuerpos de las peticiones de /api/routine. */
public final class RoutineRequests {

    public record SelectRequest(UUID activityId) {
    }

    /** mode: "refresh" (misma disciplina, otra selección) | "another" (siguiente mejor disciplina). */
    public record RegenerateRequest(String mode) {
    }

    /** reaction: "like" | "dislike". */
    public record FeedbackRequest(String reaction) {
    }

    /** Cuerpo de PUT /exercises/{id}/done. */
    public record DoneRequest(Boolean done) {
    }

    /** Respuesta del checklist: estado del ejercicio y avance de la rutina vigente. */
    public record DoneResponse(boolean done, int doneCount, int totalCount) {
    }

    /** Cuerpo de POST /routine/days: agrega un día nuevo (nombre libre, no solo "Lun"/"Mar"). */
    public record AddDayRequest(String label) {
    }

    /** Cuerpo de POST /routine/days/{dayIndex}/exercises: el Taller elige exercicio, día y reps. */
    public record AddExerciseToDayRequest(UUID exerciseId, Integer sets, Integer reps, Integer durationSec) {
    }

    /** Cuerpo de PUT /routine/exercises/{id}: edita series/reps/duración y, si viene, el día. */
    public record UpdateExerciseRequest(Integer sets, Integer reps, Integer durationSec, Integer dayIndex) {
    }

    private RoutineRequests() {
    }
}
