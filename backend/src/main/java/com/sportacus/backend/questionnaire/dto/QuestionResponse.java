package com.sportacus.backend.questionnaire.dto;

import java.util.List;

/**
 * Definición de una pregunta. {@code type} es "options", "range" o "tags" (igual
 * convención que usa el mockup). Según el tipo, el frontend usa unos campos u otros:
 * - options: usa {@code options}
 * - range: usa {@code min}, {@code max}, {@code unit}
 * - tags: usa {@code tags}
 */
public record QuestionResponse(
        String id,
        String prompt,
        String hint,
        String type,
        List<OptionResponse> options,
        Integer min,
        Integer max,
        String unit,
        List<String> tags) {
}
