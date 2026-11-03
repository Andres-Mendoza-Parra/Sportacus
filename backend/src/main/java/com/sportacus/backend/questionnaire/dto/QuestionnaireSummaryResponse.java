package com.sportacus.backend.questionnaire.dto;

/** Una fila de la pantalla "Mis cuestionarios" (qlist del mockup). */
public record QuestionnaireSummaryResponse(
        String key,
        String title,
        String description,
        String icon,
        String status, // "completo" | "parcial" | "sin_iniciar"
        int completionPct,
        int answeredCount,
        int totalCount) {
}
