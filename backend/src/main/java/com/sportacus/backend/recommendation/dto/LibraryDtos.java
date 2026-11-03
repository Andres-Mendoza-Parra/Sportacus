package com.sportacus.backend.recommendation.dto;

import java.util.List;

/** Contratos de la biblioteca de ejercicios (GET /api/library/...). */
public final class LibraryDtos {

    private LibraryDtos() {
    }

    public record LibraryExercise(String id, String name, String muscleGroup, String equipment,
            String difficultyTag, String environmentTag, String icon, String imageUrl, boolean favorite,
            boolean timed) {
    }

    public record LibraryFilters(List<String> muscles, List<String> difficulties, String moduleName) {
    }

    public record AddToRoutineRequest(String exerciseId) {
    }
}
