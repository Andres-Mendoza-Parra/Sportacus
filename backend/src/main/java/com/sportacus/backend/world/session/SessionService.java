package com.sportacus.backend.world.session;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.analytics.AnalyticsService;
import com.sportacus.backend.common.Json;
import com.sportacus.backend.engagement.EngagementService;
import com.sportacus.backend.engagement.EngagementService.Achievement;
import com.sportacus.backend.engagement.EngagementService.Streak;
import com.sportacus.backend.user.User;
import com.sportacus.backend.world.catalog.CatalogRepository;
import com.sportacus.backend.world.core.LoggedSession;
import com.sportacus.backend.world.core.LoggedSession.LoggedBlock;
import com.sportacus.backend.world.core.SessionOutcome;
import com.sportacus.backend.world.core.SessionOutcome.AchievementGrant;
import com.sportacus.backend.world.core.SessionOutcome.MetricValue;
import com.sportacus.backend.world.core.WorldContext;
import com.sportacus.backend.world.core.WorldModule;
import com.sportacus.backend.world.core.WorldRegistry;
import com.sportacus.backend.world.enrollment.UserWorldRepository;
import com.sportacus.backend.world.enrollment.WorldContextService;
import com.sportacus.backend.world.goals.AfterCommit;
import com.sportacus.backend.world.goals.GoalService;
import com.sportacus.backend.world.metrics.MetricRepository;
import com.sportacus.backend.world.plan.PlanRepository.BlockRow;
import com.sportacus.backend.world.plan.PlanService;

/**
 * Registra una sesión y aplica lo que el mundo deduce de ella: métricas de nicho, avance de
 * progresiones, estado del módulo, logros, metas, racha y, si hace falta, un plan nuevo.
 * <p>
 * Idempotente por {@code clientId}: la app genera un UUID al empezar la sesión y lo reenvía en cada reintento
 * (doble toque, sin conexión, sesión expirada); el mismo registro nunca se aplica dos veces.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    /** Horas durante las que se puede registrar el dolor del día siguiente. */
    public static final int NEXT_DAY_PAIN_WINDOW_DAYS = 3;

    private final WorldRegistry registry;
    private final SessionLogRepository logs;
    private final PlanService planService;
    private final MetricRepository metrics;
    private final UserWorldRepository userWorlds;
    private final EngagementService engagement;
    private final WorldContextService contexts;
    private final CatalogRepository catalog;
    private final com.sportacus.backend.profile.Profile360Service profile360;
    private final GoalService goals;
    private final AnalyticsService analytics;
    private final Json json;

    public SessionService(WorldRegistry registry, SessionLogRepository logs, PlanService planService,
            MetricRepository metrics, UserWorldRepository userWorlds, EngagementService engagement,
            WorldContextService contexts, CatalogRepository catalog,
            @org.springframework.context.annotation.Lazy com.sportacus.backend.profile.Profile360Service profile360,
            GoalService goals, AnalyticsService analytics, Json json) {
        this.profile360 = profile360;
        this.registry = registry;
        this.logs = logs;
        this.planService = planService;
        this.metrics = metrics;
        this.userWorlds = userWorlds;
        this.engagement = engagement;
        this.contexts = contexts;
        this.catalog = catalog;
        this.goals = goals;
        this.analytics = analytics;
        this.json = json;
    }

    public record BlockLogRequest(UUID planBlockId, String movementKey, Integer setIndex, Integer reps, Double loadKg,
            Integer rir, Integer holdS, Integer durationS, Double distanceM, Integer pain, Boolean completed) {
    }

    /** @param clientId UUID que genera la app al iniciar la sesión (opcional, recomendado) */
    public record SessionLogRequest(UUID planSessionId, String title, LocalDate performedOn, Integer durationMin,
            Integer rpe, Integer painBefore, Integer painAfter, Integer moodBefore, Integer moodAfter, Double distanceM,
            String notes, List<BlockLogRequest> blocks, UUID clientId) {
    }

    /** @param duplicate true si es el reenvío de un registro ya guardado (mismo clientId) */
    public record SessionResult(UUID sessionLogId, List<String> highlights, List<Achievement> achievements,
            Streak streak, List<Unlock> unlocks, boolean planRegenerated,
            List<com.sportacus.backend.profile.Profile360Service.NextItem> ask, boolean duplicate) {
    }

    public record Unlock(String progressionKey, String progressionName, int fromStep, int toStep, String stepName) {
    }

    @Transactional
    public SessionResult log(WorldContext ctx, SessionLogRequest req) {
        LocalDate today = LocalDate.now(MetricRepository.ZONE);
        SessionValidation.validate(req, today);
        UUID uid = ctx.user().userId();
        if (req.clientId() != null) {
            logs.lockClient(uid, req.clientId());
            var previous = logs.byClient(uid, req.clientId());
            if (previous.isPresent()) return replay(uid, previous.get());
        }
        WorldModule module = registry.require(ctx.world().worldKey());
        String kind = null;
        String title = blankToNull(req.title());
        Map<UUID, BlockRow> planned = new HashMap<>();
        if (req.planSessionId() != null) {
            var ref = logs.planSession(req.planSessionId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesión del plan no encontrada"));
            if (!ref.userWorldId().equals(ctx.world().id())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esa sesión no es de tu plan");
            }
            kind = ref.kind();
            if (title == null) title = ref.title();
            // Bloques del plan dueño de la sesión, aunque se haya regenerado después (auditoría A2).
            logs.plannedBlocks(req.planSessionId()).forEach(b -> planned.put(b.id(), b));
        }

        List<LoggedBlock> blocks = new ArrayList<>();
        for (BlockLogRequest b : req.blocks() == null ? List.<BlockLogRequest>of() : req.blocks()) {
            BlockRow p = b.planBlockId() == null ? null : planned.get(b.planBlockId());
            String movement = b.movementKey() != null ? b.movementKey() : p == null ? null : p.movementKey();
            if (movement != null && catalog.movement(movement).isEmpty()) movement = null;
            blocks.add(new LoggedBlock(p == null ? null : p.id(), movement, b.setIndex() == null ? 0 : b.setIndex(),
                    b.reps(), b.loadKg(), b.rir(), b.holdS(), b.durationS(), b.distanceM(), b.pain(),
                    b.completed() == null || b.completed(), p == null ? null : p.blockType(),
                    p == null ? null : p.prescription(), p == null ? null : p.progressionKey(),
                    p == null ? null : p.stepIndex()));
        }
        LocalDate day = req.performedOn() == null ? today : req.performedOn();
        LoggedSession draft = new LoggedSession(null, req.planSessionId(), kind, day, req.durationMin(), req.rpe(),
                req.painBefore(), req.painAfter(), req.moodBefore(), req.moodAfter(), req.distanceM(), req.notes(),
                blocks);
        UUID logId = logs.insert(uid, ctx.world().id(), title, draft, req.clientId());
        LoggedSession logged = new LoggedSession(logId, draft.planSessionId(), kind, day, draft.durationMin(),
                draft.rpe(), draft.painBefore(), draft.painAfter(), draft.moodBefore(), draft.moodAfter(),
                draft.distanceM(), draft.notes(), blocks);

        // Métricas comunes a todos los mundos.
        UUID uw = ctx.world().id();
        String ref = logId.toString();
        OffsetDateTime at = MetricRepository.momentOf(day);
        if (logged.durationMin() != null) metrics.insert(uid, uw, "minutos_sesion", logged.durationMin(), "sesion", ref, null, at);
        if (logged.rpe() != null) metrics.insert(uid, uw, "rpe_sesion", logged.rpe(), "sesion", ref, null, at);
        if (logged.sessionLoad() != null) metrics.insert(uid, uw, "carga_sesion", logged.sessionLoad(), "sesion", ref, null, at);

        SessionOutcome outcome = module.evaluateSession(ctx, logged);
        List<Unlock> unlocks = apply(ctx, outcome, "sesion", ref, at);
        userWorlds.touch(uw);

        List<String> highlights = new ArrayList<>(outcome.highlights());
        List<Achievement> fresh = new ArrayList<>();
        String worldName = catalog.world(ctx.world().worldKey()).map(w -> w.name()).orElse(ctx.world().worldKey());
        for (AchievementGrant g : outcome.achievements()) {
            if (engagement.grant(uid, ctx.world().worldKey(), g.key(), g.title(), g.icon())) {
                fresh.add(new Achievement(g.key(), ctx.world().worldKey(), g.title(), g.icon(), null));
            }
        }
        Streak streak = engagement.refreshStreak(uid);
        fresh.addAll(engagement.genericAfterSession(uid, uw, ctx.world().worldKey(), worldName, streak));
        for (var goal : goals.check(uw)) {
            highlights.add(GoalService.ICON + " ¡Meta lograda! " + goal.label());
            fresh.add(new Achievement(GoalService.achievementKey(goal.goalKey()), ctx.world().worldKey(),
                    "Meta lograda: " + goal.label(), GoalService.ICON, goal.achievedAt()));
        }

        boolean regenerated = false;
        if (outcome.regeneratePlan() || !unlocks.isEmpty()) {
            planService.regenerate(contexts.reload(ctx));
            regenerated = true;
        }
        // Tras entrenar es buen momento para una pregunta corta (sueño, ánimo, hábitos). Nunca rompe el registro.
        List<com.sportacus.backend.profile.Profile360Service.NextItem> ask = List.of();
        try {
            ask = profile360.next(uid, 3, "post_session").stream().filter(i -> "question".equals(i.kind())).limit(1)
                    .toList();
        } catch (RuntimeException e) {
            ask = List.of();
        }
        SessionResult result = new SessionResult(logId, highlights, fresh, streak, unlocks, regenerated, ask, false);
        if (req.clientId() != null) logs.saveResult(logId, json.write(result));

        String world = ctx.world().worldKey();
        Map<String, Object> props = new HashMap<>();
        props.put("world", world);
        props.put("minutes", logged.durationMin() == null ? 0 : logged.durationMin());
        props.put("fromPlan", req.planSessionId() != null);
        AfterCommit.run(() -> analytics.track(uid, "session_logged", props));
        return result;
    }

    /** Reenvío del mismo registro: se devuelve lo ya guardado, sin volver a aplicar efectos. */
    private SessionResult replay(UUID userId, SessionLogRepository.Previous previous) {
        if (previous.result() != null) {
            try {
                SessionResult r = json.read(previous.result(), SessionResult.class);
                return new SessionResult(r.sessionLogId(), r.highlights(), r.achievements(), r.streak(), r.unlocks(),
                        r.planRegenerated(), List.of(), true);
            } catch (RuntimeException e) {
                log.warn("No se pudo leer el resultado guardado de una sesión ({})", e.getClass().getSimpleName());
            }
        }
        return new SessionResult(previous.id(), List.of(), List.of(), engagement.streak(userId), List.of(), false,
                List.of(), true);
    }

    /**
     * Aplica efectos (métricas, escalones, estado, nivel). Lo usan sesiones y tests.
     * @return escalones que avanzaron
     */
    public List<Unlock> apply(WorldContext ctx, SessionOutcome outcome, String source, String ref) {
        return apply(ctx, outcome, source, ref, OffsetDateTime.now());
    }

    public List<Unlock> apply(WorldContext ctx, SessionOutcome outcome, String source, String ref, OffsetDateTime at) {
        if (outcome == null) return List.of();
        UUID uid = ctx.user().userId();
        UUID uw = ctx.world().id();
        for (MetricValue m : outcome.metrics()) {
            if (catalog.metric(m.key()).isEmpty()) {
                throw new IllegalStateException("El mundo " + ctx.world().worldKey() + " emitió una métrica no declarada: " + m.key());
            }
            metrics.insert(uid, uw, m.key(), m.value(), source, ref, m.context(), at);
        }
        List<Unlock> unlocks = new ArrayList<>();
        Map<String, Integer> before = userWorlds.progressionSteps(uw);
        outcome.progressionSteps().forEach((key, step) -> {
            int prev = before.getOrDefault(key, 0);
            userWorlds.setProgressionStep(uw, key, step);
            if (step > prev) {
                var prog = catalog.progression(ctx.world().worldKey(), key).orElse(null);
                unlocks.add(new Unlock(key, prog == null ? key : prog.name(), prev, step,
                        prog == null ? null : prog.step(step).name()));
            }
        });
        userWorlds.mergeState(uw, outcome.stateUpdates());
        if (outcome.newLevel() != null) userWorlds.setLevel(uw, outcome.newLevel());
        return unlocks;
    }

    /**
     * Dolor a las 24 h de una sesión (auditoría A7). Se resuelve el mundo desde el registro (no hace falta que siga
     * siendo el activo), una sola vez por registro (409 si ya estaba) y solo para sesiones de los últimos 3 días.
     */
    @Transactional
    public void nextDayPain(User user, UUID sessionLogId, int pain) {
        if (pain < 0 || pain > 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El dolor va de 0 a 10");
        var target = logs.lockForPain(sessionLogId).filter(t -> t.userId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesión no encontrada"));
        if (target.painNextDay() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya registraste el dolor del día siguiente de esta sesión");
        }
        LocalDate today = LocalDate.now(MetricRepository.ZONE);
        if (target.performedOn().isBefore(today.minusDays(NEXT_DAY_PAIN_WINDOW_DAYS))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El dolor del día siguiente se registra dentro de las 72 horas posteriores a la sesión");
        }
        if (!logs.setNextDayPain(sessionLogId, pain)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya registraste el dolor del día siguiente de esta sesión");
        }
        WorldContext ctx = contexts.forUserWorld(user, target.userWorldId());
        metrics.insert(ctx.user().userId(), ctx.world().id(), "dolor_24h", pain, "sesion", sessionLogId.toString(), null);
        SessionOutcome outcome = registry.require(ctx.world().worldKey()).onNextDayPain(ctx, sessionLogId, pain);
        List<Unlock> changed = apply(ctx, outcome, "sesion", sessionLogId.toString());
        goals.check(ctx.world().id());
        if (outcome != null && (outcome.regeneratePlan() || !changed.isEmpty())) {
            planService.regenerate(contexts.reload(ctx));
        }
    }

    /**
     * Ajuste manual de escalón: solo un paso hacia arriba o hacia abajo, dentro de la progresión y si el mundo lo
     * permite. Regenera el plan con el escalón nuevo.
     */
    @Transactional
    public int adjustStep(WorldContext ctx, String progressionKey, int requested) {
        if (!registry.require(ctx.world().worldKey()).allowsManualSteps()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "En este mundo la progresión la guían tus registros por seguridad");
        }
        var prog = catalog.progression(ctx.world().worldKey(), progressionKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Progresión no encontrada"));
        int current = userWorlds.progressionSteps(ctx.world().id()).getOrDefault(progressionKey, 0);
        int target = Math.max(0, Math.min(prog.lastIndex(), requested));
        if (Math.abs(target - current) > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo puedes moverte un escalón a la vez");
        }
        if (target != current) {
            userWorlds.setProgressionStep(ctx.world().id(), progressionKey, target);
            goals.check(ctx.world().id());
            planService.regenerate(contexts.reload(ctx));
        }
        return target;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
