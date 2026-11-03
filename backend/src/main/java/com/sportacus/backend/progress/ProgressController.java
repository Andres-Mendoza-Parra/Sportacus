package com.sportacus.backend.progress;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sportacus.backend.progress.dto.ProgressDtos.MetricRequest;
import com.sportacus.backend.progress.dto.ProgressDtos.ProgressSummary;
import com.sportacus.backend.progress.dto.ProgressDtos.SessionRequest;
import com.sportacus.backend.progress.dto.ProgressDtos.SessionResult;
import com.sportacus.backend.user.User;

/** Progreso del usuario: sesiones cerradas, racha, logros, retos y mediciones. */
@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService service;

    public ProgressController(ProgressService service) {
        this.service = service;
    }

    @GetMapping
    public ProgressSummary summary(@AuthenticationPrincipal User currentUser) {
        return service.summary(currentUser.getId());
    }

    @PostMapping("/sessions")
    public SessionResult closeSession(@AuthenticationPrincipal User currentUser,
            @RequestBody(required = false) SessionRequest request) {
        return service.closeSession(currentUser.getId(), request);
    }

    @PostMapping("/metrics")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveMetric(@AuthenticationPrincipal User currentUser, @RequestBody MetricRequest request) {
        service.saveMetric(currentUser.getId(), request);
    }
}
