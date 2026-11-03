package com.sportacus.backend.recommendation.dto;

/** Una tarjeta de ejercicio dentro de un día de la rutina (routine screen del mockup). */
public record RoutineExerciseResponse(
        String exerciseRecommendationId,
        String exerciseId,
        String name,
        String icon,
        String detailLabel, // ej: "4 series × 8-10 reps" o "3 series × 30 seg"
        String muscleGroupTag,
        String difficultyTag,
        boolean logged,
        String imageUrl, // miniatura (wger) o null
        boolean done, // checklist: hecho / pendiente
        Integer sets, Integer reps, Integer durationSec) { // valores crudos, para editarlos en el Taller
}
