package com.sportacus.backend.questionnaire.dto;

/** Respuesta de POST /api/questionnaires/{key}: el guardado invalida la rutina vigente. */
public record QuestionnaireSaveResponse(boolean routeInvalidated, int profileStrengthPct) {
}
