package com.sportacus.backend.dashboard.dto;

import java.util.List;

import com.sportacus.backend.dashboard.dto.DashboardParts.FocusResponse;
import com.sportacus.backend.dashboard.dto.DashboardParts.InsightResponse;
import com.sportacus.backend.dashboard.dto.DashboardParts.MetricResponse;
import com.sportacus.backend.dashboard.dto.DashboardParts.ScorePart;
import com.sportacus.backend.dashboard.dto.DashboardParts.TodayResponse;

/**
 * Respuesta completa de GET /api/dashboard. Los cinco primeros campos mantienen el contrato v1
 * (radar = énfasis muscular de la rutina; progress = composición por categoría); el resto es v2.
 */
public record DashboardResponse(
        String displayName,
        StatsResponse stats,
        List<RadarPointResponse> radar,
        List<ProgressItemResponse> progress,
        CurrentActivityResponse currentActivity,
        String goalKey,
        String goalLabel,
        FocusResponse focus,
        TodayResponse today, // null si no hay rutina
        List<MetricResponse> metrics,
        List<InsightResponse> insights,
        int strengthPct,
        List<ScorePart> scoreBreakdown) {
}
