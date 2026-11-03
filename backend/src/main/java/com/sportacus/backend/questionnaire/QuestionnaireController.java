package com.sportacus.backend.questionnaire;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.questionnaire.dto.QuestionnaireDetailResponse;
import com.sportacus.backend.questionnaire.dto.QuestionnaireListResponse;
import com.sportacus.backend.questionnaire.dto.QuestionnaireSaveResponse;
import com.sportacus.backend.user.User;

@RestController
@RequestMapping("/api/questionnaires")
public class QuestionnaireController {

    private final QuestionnaireService service;

    public QuestionnaireController(QuestionnaireService service) {
        this.service = service;
    }

    @GetMapping
    public QuestionnaireListResponse list(@AuthenticationPrincipal User currentUser) {
        return service.listQuestionnaires(currentUser.getId());
    }

    @GetMapping("/{key}")
    public QuestionnaireDetailResponse detail(@AuthenticationPrincipal User currentUser, @PathVariable String key) {
        try {
            return service.getQuestionnaire(currentUser.getId(), key);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/{key}")
    public QuestionnaireSaveResponse save(@AuthenticationPrincipal User currentUser, @PathVariable String key,
            @RequestBody(required = false) Map<String, Object> answers) {
        try {
            return service.saveAnswers(currentUser.getId(), key, answers);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
