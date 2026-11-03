package com.sportacus.backend.recommendation.dto;

import java.util.List;

/** GET /api/routine y respuesta de select / regenerate / swap. */
public record RoutineResponse(
        String activityName,
        String description,
        Integer durationMin,
        String intensityLabel,
        String goalLabel,
        int affinityScore,
        String rationale,
        List<RoutineDayResponse> days,
        String routeId,
        String activityId,
        String generatedAt, // ISO-8601
        int profileStrengthPct,
        String engineVersion,
        int alternativesCount,
        int doneCount,
        int totalCount,
        List<ScoreFactorResponse> breakdown) { // desglose del score de afinidad
}
