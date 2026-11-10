package com.sportacus.backend.world.enrollment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.analytics.AnalyticsService;
import com.sportacus.backend.common.Answers;
import com.sportacus.backend.profile.Profile360Service;
import com.sportacus.backend.profile.ProfileForm;
import com.sportacus.backend.profile.ProfileRepository;
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
import com.sportacus.backend.world.goals.AfterCommit;
import com.sportacus.backend.world.goals.GoalService;
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
    private final ProfileRepository profiles;
    private final Profile360Service profile360;
    private final GoalService goals;
    private final AnalyticsService analytics;

    public EnrollmentService(CatalogRepository catalog, WorldRegistry registry, UserWorldRepository userWorlds,
            UserContextService userContexts, PlanService planService, DiscoveryService discovery,
            SessionLogRepository sessionLogs, JdbcTemplate jdbc, ProfileRepository profiles,
            Profile360Service profile360, GoalService goals, AnalyticsService analytics) {
        this.goals = goals;
        this.analytics = analytics;
        this.profiles = profiles;
        this.profile360 = profile360;
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
            if (BASE.equals(w.key())) continue; // Mi Base no se elige: todos la tienen
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
        FormSpec raw = module.onboardingForm(uc);
        List<FormSpec.Question> essentials = missingEssentials(uc);
        if (!essentials.isEmpty()) {
            List<FormSpec.Question> all = new ArrayList<>(essentials);
            all.addAll(raw.questions());
            raw = new FormSpec(raw.key(), raw.title(), raw.subtitle(), all, raw.answers());
        }
        FormSpec form = raw;
        return userWorlds.find(user.getId(), worldKey).map(uw -> form.withAnswers(uw.profile().asMap())).orElse(form);
    }

    /**
     * Inscribe en un mundo. Si ya estaba inscrito es el <b>modo edición</b> ("Afina tu plan", auditoría A1): se
     * fusionan las respuestas y se conservan escalones (el mayor entre el actual y el nuevo), mejores marcas, estado
     * vivo, nivel y metas; solo cambia lo que dependen de las respuestas editadas.
     */
    @Transactional
    public EnrollResponse enroll(User user, String worldKey, Answers answers) {
        WorldModule module = requireAvailable(worldKey);
        userWorlds.lockUser(user.getId());
        Answers raw = answers == null ? Answers.empty() : answers;
        Map<String, Object> essentials = new java.util.LinkedHashMap<>();
        for (String id : PLAN_ESSENTIALS) if (raw.has(id)) essentials.put(id, raw.values().get(id));
        if (!essentials.isEmpty()) {
            Answers checked = FormAnswersValidator.validate(ProfileForm.form(), new Answers(essentials));
            profiles.save(user.getId(), checked);
            profile360.recordProfileForm(user.getId(), checked);
        }
        ensureBase(user);
        UserContext uc = userContexts.of(user);
        if (!missingEssentials(uc).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cuéntanos cuántos días por semana y cuántos minutos por sesión tienes para armar tu plan");
        }
        UserWorldData previous = userWorlds.find(user.getId(), worldKey).orElse(null);
        boolean edit = previous != null;
        UUID uwId;
        EnrollmentResult result;
        if (!edit) {
            result = module.enroll(uc, raw);
            checkTrack(worldKey, result);
            uwId = userWorlds.enroll(user.getId(), worldKey, result.trackKey(), result.level(), result.profile(),
                    result.state());
            userWorlds.replaceGoals(uwId, result.goals());
            userWorlds.replaceProgressions(uwId, result.progressions());
        } else {
            Answers merged = new Answers(EnrollmentMerge.answers(previous.profile().asMap(), raw.values()));
            result = module.enroll(uc, merged);
            checkTrack(worldKey, result);
            EnrollmentResult before = module.enroll(uc, previous.profile());
            Map<String, Object> state = EnrollmentMerge.state(previous.state(), before.state(), result.state());
            uwId = userWorlds.enroll(user.getId(), worldKey, result.trackKey(), previous.level(), result.profile(),
                    state);
            userWorlds.addMissingGoals(uwId, result.goals());
            userWorlds.mergeProgressions(uwId,
                    EnrollmentMerge.progressions(userWorlds.progressionSteps(uwId), result.progressions()));
        }
        goals.initBaselines(uwId);
        jdbc.update("UPDATE app_user SET onboarding_completed_at = COALESCE(onboarding_completed_at, now()) WHERE id = ?",
                user.getId());
        discovery.markChosen(user.getId(), worldKey);
        UserWorldData uw = userWorlds.byId(uwId).orElseThrow();
        PlanView plan = planService.regenerate(new WorldContext(uc, uw));
        UUID uid = user.getId();
        String mode = edit ? "edit" : "new";
        AfterCommit.run(() -> analytics.track(uid, "world_enrolled", Map.of("world", worldKey, "mode", mode)));
        return new EnrollResponse(toMyWorld(uw), result.rationale(), result.notices(), plan);
    }

    private void checkTrack(String worldKey, EnrollmentResult result) {
        if (result.trackKey() != null && catalog.track(worldKey, result.trackKey()).isEmpty()) {
            throw new IllegalStateException("El mundo " + worldKey + " eligió una ruta inexistente: " + result.trackKey());
        }
    }

    @Transactional
    public MyWorld activate(User user, String worldKey) {
        userWorlds.lockUser(user.getId());
        UserWorldData uw = userWorlds.find(user.getId(), worldKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Primero completa el onboarding de ese mundo"));
        boolean changed = !uw.active();
        userWorlds.activate(user.getId(), worldKey);
        if (changed) {
            UUID uid = user.getId();
            AfterCommit.run(() -> analytics.track(uid, "world_activated", Map.of("world", worldKey)));
        }
        return toMyWorld(userWorlds.byId(uw.id()).orElseThrow());
    }

    public List<MyWorld> myWorlds(User user) {
        ensureBase(user);
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

    // ── Mi Base (mundo 0) ─────────────────────────────────────

    public static final String BASE = "base";

    /**
     * Garantiza que el usuario esté inscrito en Mi Base. Si no tiene ningún mundo activo, Mi Base queda activo; si ya
     * tiene otro, Mi Base queda en pausa (disponible en el selector).
     */
    @Transactional
    public void ensureBase(User user) {
        ensureBase(user.getId(), user.visibleName());
    }

    @Transactional
    public void ensureBase(UUID userId, String name) {
        if (userWorlds.find(userId, BASE).isPresent() || !registry.isImplemented(BASE)) return;
        userWorlds.lockUser(userId);
        if (userWorlds.find(userId, BASE).isPresent()) return; // otra petición la creó mientras esperábamos
        String previous = userWorlds.findActive(userId).map(UserWorldData::worldKey).orElse(null);
        UserContext uc = userContexts.of(userId, name);
        EnrollmentResult result = registry.require(BASE).enroll(uc, Answers.empty());
        UUID uwId = userWorlds.enroll(userId, BASE, result.trackKey(), result.level(), result.profile(), result.state());
        userWorlds.replaceGoals(uwId, result.goals());
        if (previous != null) {
            userWorlds.activate(userId, previous);
        } else {
            planService.regenerate(new WorldContext(uc, userWorlds.byId(uwId).orElseThrow()));
        }
    }

    /** Preguntas del perfil común que un plan necesita sí o sí; se anteponen al formulario del mundo si faltan. */
    private static final List<String> PLAN_ESSENTIALS = List.of("p_days", "p_minutes");

    private List<FormSpec.Question> missingEssentials(UserContext uc) {
        List<FormSpec.Question> out = new ArrayList<>();
        for (FormSpec.Question q : ProfileForm.form().questions()) {
            if (!PLAN_ESSENTIALS.contains(q.id())) continue;
            boolean missing = q.id().equals("p_days") ? uc.profile().daysPerWeek() == null
                    : uc.profile().minutesPerSession() == null;
            if (missing) out.add(q);
        }
        return out;
    }
}
