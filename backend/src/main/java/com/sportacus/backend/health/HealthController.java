package com.sportacus.backend.health;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.user.User;
import com.sportacus.backend.world.form.FormSpec;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/screening/form")
    public FormSpec form(@AuthenticationPrincipal User user) {
        return healthService.form(user.getId());
    }

    @PostMapping("/screening")
    public HealthSummary save(@AuthenticationPrincipal User user, @RequestBody Map<String, Object> answers) {
        return healthService.saveScreening(user.getId(), new Answers(answers));
    }

    @GetMapping("/summary")
    public HealthSummary summary(@AuthenticationPrincipal User user) {
        return healthService.summary(user.getId());
    }

    @PostMapping("/clearance")
    public HealthSummary clearance(@AuthenticationPrincipal User user) {
        return healthService.confirmClearance(user.getId());
    }
}
