package com.sportacus.backend.dashboard.dto;

import java.util.List;

/** Bloques nuevos del dashboard v2 (todo se calcula en vivo con el perfil, la rutina vigente y el checklist). */
public final class DashboardParts {

    /** Un dato del gráfico principal. value 0-100; detail y color son opcionales. */
    public record FocusItem(String key, String label, int value, String detail, String color) {
    }

    /** Gráfico principal. type: "radar" | "donut" | "bars" | "gauge" (elegido según la meta del usuario). */
    public record FocusResponse(String type, String title, String subtitle, List<FocusItem> items) {
    }

    /** Avance del checklist de la rutina vigente (lista de tareas, no historial). */
    public record TodayResponse(String routineName, int doneCount, int totalCount, int pct) {
    }

    /** Indicador del perfil. status: "good" | "warn" | "low". pct (0-100) es opcional y alimenta una barra. */
    public record MetricResponse(String key, String label, String value, String unit, String status, String hint,
            Integer pct) {
    }

    /** Consejo accionable. tone: "good" | "warn" | "info". route: pantalla sugerida (p. ej. "/questionnaires"). */
    public record InsightResponse(String icon, String title, String text, String tone, String route) {
    }

    /** Componente del score global: value 0-100 con su peso en porcentaje. */
    public record ScorePart(String key, String label, int value, int weightPct) {
    }

    private DashboardParts() {
    }
}
