package com.sportacus.backend.dashboard.dto;

/** Las 3 tarjetas superiores del dashboard. */
public record StatsResponse(int globalScore, int profileCompletionPct, int activeModules) {
}
