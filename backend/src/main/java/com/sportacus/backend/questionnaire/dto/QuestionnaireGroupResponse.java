package com.sportacus.backend.questionnaire.dto;

import java.util.List;

public record QuestionnaireGroupResponse(String groupTitle, List<QuestionnaireSummaryResponse> items) {
}
