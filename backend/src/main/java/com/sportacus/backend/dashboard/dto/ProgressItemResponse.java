package com.sportacus.backend.dashboard.dto;

/** Una barra de "Progreso por objetivo" (Hipertrofia, Cardio, Movilidad...). */
public record ProgressItemResponse(String key, String label, String icon, String color, int progressPct) {
}
