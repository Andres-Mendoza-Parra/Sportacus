package com.sportacus.backend.world.enrollment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.profile.UserContextService;
import com.sportacus.backend.user.User;
import com.sportacus.backend.world.catalog.CatalogModels.TrackInfo;
import com.sportacus.backend.world.catalog.CatalogModels.WorldInfo;
import com.sportacus.backend.world.catalog.CatalogRepository;
import com.sportacus.backend.world.core.EnrollmentResult;
import com.sportacus.backend.world.core.Factor;
import com.sportacus.backend.world.core.PlanView;
import com.sportacus.backend.world.core.UserContext;
import com.sportacus.backend.world.core.UserWorldData;
import com.sportacus.backend.world.core.WorldContext;
import com.sportacus.backend.world.core.WorldModule;
import com.sportacus.backend.world.core.WorldRegistry;
import com.sportacus.backend.world.discovery.DiscoveryService;
import com.sportacus.backend.world.form.FormSpec;
import com.sportacus.backend.world.plan.PlanService;
import com.sportacus.backend.world.session.SessionLogRepository;

/** Catálogo de mundos para el usuario, inscripción, cambio de mundo activo. */
@Service
public class EnrollmentService {

    private final CatalogRepository catalog;
    private final WorldRegistry registry;
    private final UserWorldRepository userWorlds;
    private final UserContextService userContexts;
    private final PlanService planService;
    private final DiscoveryService discovery;
    private final SessionLogRepository sessionLogs;
    private final JdbcTemplate jdbc;

    public EnrollmentService(CatalogRepository catalog, WorldRegistry registry, UserWorldRepository userWorlds,
            UserContextService userContexts, PlanService planService, DiscoveryService discovery,
            SessionLogRepository sessionLogs, JdbcTemplate jdbc) {
        this.catalog = catalog;
        this.registry = registry;
        this.userWorlds = userWorlds;
        this.userContexts = userContexts;
        this.planService = planService;
        this.discovery = discovery;
        this.sessionLogs = sessionLogs;
        this.jdbc = jdbc;
    }

    public record WorldCard(String key, String name, String tagline, String description, String icon, String color,
            String status, boolean available, List<String> highlights, List<TrackInfo> tracks, boolean enrolled,
            boolean active) {
    }

    public record MyWorld(String key, String name, String icon, String color, String trackKey, String trackName,
            int level, boolean active, int sessions) {
    }

    public record EnrollResponse(MyWorld world, List<Factor> rationale, List<String> notices, PlanView plan) {
    }

    public List<WorldCard> catalogFor(User user) {
        List<UserWorldData> mine = userWorlds.list(user.getId());
        List<WorldCard> out = new ArrayList<>();
        for (WorldInfo w : catalog.worlds()) {
            UserWorldData uw = mine.stream().filter(m -> m.worldKey().equals(w.key())).findFirst().orElse(null);
            out.add(new WorldCard(w.key(), w.name(), w.tagline(), w.description(), w.icon(), w.color(), w.status(),
                    w.selectable() && registry.isImplemented(w.key()), w.highlights(), w.tracks(), uw != null,
                    uw != null && uw.active()));
        }
        return out;
    }

    public FormSpec onboardingForm(User user, String worldKey) {
        WorldModule module = requireAvailable(worldKey);
        UserContext uc = userContexts.of(user);
        FormSpec form = module.onboardingForm(uc);
        return userWorlds.find(user.getId(), worldKey).map(uw -> form.withAnswers(uw.profile().asMap())).orElse(form);
    }

    @Transactional
    public EnrollResponse enroll(User user, String worldKey, Answers answers) {
        WorldModule module = requireAvailable(worldKey);
        UserContext uc = userContexts.of(user);
        EnrollmentResult result = module.enroll(uc, answers);
        if (result.trackKey() != null && catalog.track(worldKey, result.trackKey()).isEmpty()) {
            throw new IllegalStateException("El mundo " + worldKey + " eligió una ruta inexistente: " + result.trackKey());
        }
        UUID uwId = userWorlds.enroll(user.getId(), worldKey, result.trackKey(), result.level(), result.profile(),
                result.state());
        userWorlds.replaceGoals(uwId, result.goals());
        userWorlds.replaceProgressions(uwId, result.progressions());
        jdbc.update("UPDATE app_user SET onboarding_completed_at = COALESCE(onboarding_completed_at, now()) WHERE id = ?",
                user.getId());
        discovery.markChosen(user.getId(), worldKey);
        UserWorldData uw = userWorlds.byId(uwId).orElseThrow();
        PlanView plan = planService.regenerate(new WorldContext(uc, uw));
        return new EnrollResponse(toMyWorld(uw), result.rationale(), result.notices(), plan);
    }

    @Transactional
    public MyWorld activate(User user, String worldKey) {
        UserWorldData uw = userWorlds.find(user.getId(), worldKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Primero completa el onboarding de ese mundo"));
        userWorlds.activate(user.getId(), worldKey);
        return toMyWorld(userWorlds.byId(uw.id()).orElseThrow());
    }

    public List<MyWorld> myWorlds(User user) {
        return userWorlds.list(user.getId()).stream().map(this::toMyWorld).toList();
    }

    private MyWorld toMyWorld(UserWorldData uw) {
        WorldInfo w = catalog.world(uw.worldKey()).orElseThrow();
        TrackInfo t = uw.trackKey() == null ? null : catalog.track(uw.worldKey(), uw.trackKey()).orElse(null);
        return new MyWorld(w.key(), w.name(), w.icon(), w.color(), uw.trackKey(), t == null ? null : t.name(),
                uw.level(), uw.active(), sessionLogs.countWorld(uw.id()));
    }

    private WorldModule requireAvailable(String worldKey) {
        WorldInfo w = catalog.world(worldKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mundo no encontrado"));
        if (!w.selectable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, w.name() + " llegará pronto");
        }
        return registry.require(worldKey);
    }
}
