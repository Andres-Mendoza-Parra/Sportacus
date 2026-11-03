package com.sportacus.backend.recommendation.dto;

/** Una disciplina candidata (GET /api/routine/options). */
public record RouteOptionResponse(
        String activityId,
        String name,
        String description,
        int score,
        String rationale,
        String intensityLabel,
        Integer durationMin,
        String environment,
        boolean selected) {
}
