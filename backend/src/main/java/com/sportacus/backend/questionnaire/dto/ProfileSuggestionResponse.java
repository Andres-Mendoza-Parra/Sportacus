package com.sportacus.backend.questionnaire.dto;

/** Plantilla sugerida para afinar la recomendación (la de mayor ganancia pendiente). */
public record ProfileSuggestionResponse(String questionnaireKey, String title, String reason, int impactPct) {
}
