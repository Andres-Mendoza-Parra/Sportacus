package com.sportacus.backend.questionnaire.dto;

import java.util.List;
import java.util.Map;

/** GET /api/questionnaires/{key}: las preguntas + las respuestas ya guardadas (para precargar). */
public record QuestionnaireDetailResponse(
        String key,
        String title,
        List<QuestionResponse> questions,
        Map<String, Object> answers) {
}
