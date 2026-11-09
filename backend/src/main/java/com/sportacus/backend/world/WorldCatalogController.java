package com.sportacus.backend.world;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.profile.UserContextService;
import com.sportacus.backend.user.User;
import com.sportacus.backend.world.discovery.DiscoveryService;
import com.sportacus.backend.world.discovery.DiscoveryService.DiscoveryResult;
import com.sportacus.backend.world.enrollment.EnrollmentService;
import com.sportacus.backend.world.enrollment.EnrollmentService.EnrollResponse;
import com.sportacus.backend.world.enrollment.EnrollmentService.MyWorld;
import com.sportacus.backend.world.enrollment.EnrollmentService.WorldCard;
import com.sportacus.backend.world.form.FormSpec;

/** Galería de mundos, descubrimiento e inscripción (docs/16 §Mundos). */
@RestController
@RequestMapping("/api")
public class WorldCatalogController {

    private final EnrollmentService enrollment;
    private final DiscoveryService discovery;
    private final UserContextService userContexts;

    public WorldCatalogController(EnrollmentService enrollment, DiscoveryService discovery,
            UserContextService userContexts) {
        this.enrollment = enrollment;
        this.discovery = discovery;
        this.userContexts = userContexts;
    }

    @GetMapping("/worlds")
    public List<WorldCard> worlds(@AuthenticationPrincipal User user) {
        return enrollment.catalogFor(user);
    }

    @GetMapping("/worlds/discovery/form")
    public FormSpec discoveryForm() {
        return discovery.form();
    }

    @PostMapping("/worlds/discovery")
    public DiscoveryResult discover(@AuthenticationPrincipal User user, @RequestBody Map<String, Object> answers) {
        return discovery.discover(userContexts.of(user), new Answers(answers));
    }

    @GetMapping("/worlds/{key}/onboarding")
    public FormSpec onboarding(@AuthenticationPrincipal User user, @PathVariable String key) {
        return enrollment.onboardingForm(user, key);
    }

    @PostMapping("/worlds/{key}/enroll")
    public EnrollResponse enroll(@AuthenticationPrincipal User user, @PathVariable String key,
            @RequestBody Map<String, Object> answers) {
        return enrollment.enroll(user, key, new Answers(answers));
    }

    @GetMapping("/me/worlds")
    public List<MyWorld> myWorlds(@AuthenticationPrincipal User user) {
        return enrollment.myWorlds(user);
    }

    @PostMapping("/me/worlds/{key}/activate")
    public MyWorld activate(@AuthenticationPrincipal User user, @PathVariable String key) {
        return enrollment.activate(user, key);
    }
}
