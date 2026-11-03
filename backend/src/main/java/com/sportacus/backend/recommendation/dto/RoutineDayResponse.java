package com.sportacus.backend.recommendation.dto;

import java.util.List;

public record RoutineDayResponse(String dayLabel, List<RoutineExerciseResponse> exercises) {
}
