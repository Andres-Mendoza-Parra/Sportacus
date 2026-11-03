package com.sportacus.backend.questionnaire.dto;

import java.util.List;

public record QuestionnaireListResponse(int overallCompletionPct, List<QuestionnaireGroupResponse> groups) {
}
