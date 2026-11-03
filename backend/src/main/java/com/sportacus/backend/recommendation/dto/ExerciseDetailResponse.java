package com.sportacus.backend.recommendation.dto;

import java.util.List;

import com.sportacus.backend.media.ExerciseMedia;

/** GET /api/routine/exercises/{id} (exdetail screen del mockup) enriquecido con material de wger. */
public record ExerciseDetailResponse(
        String exerciseRecommendationId,
        String name,
        String description,
        String environmentTag,
        String difficultyTag,
        String muscleGroupTag,
        String detailLabel,
        List<String> steps,
        List<String> musclesPrimary,
        List<String> musclesSecondary,
        String contraindications,
        boolean logged,
        List<String> imageUrls,
        String videoUrl,
        List<String> muscleImageUrls,
        List<ExerciseMedia.MuscleView> muscleViews,
        List<String> instructions,
        List<String> tips,
        String equipment,
        String mediaSource,
        String reaction, // "like" | "dislike" | null
        boolean done) { // checklist de la rutina
}
