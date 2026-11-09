package com.sportacus.backend.world.session;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.engagement.EngagementService;
import com.sportacus.backend.engagement.EngagementService.Achievement;
import com.sportacus.backend.engagement.EngagementService.Streak;
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
import com.sportacus.backend.world.metrics.MetricRepository;
import com.sportacus.backend.world.plan.PlanRepository;
import com.sportacus.backend.world.plan.PlanRepository.BlockRow;
import com.sportacus.backend.world.plan.PlanService;

/**
 * Registra una sesión y aplica lo que el mundo deduce de ella: métricas de nicho, avance de
 * progresiones, estado del módulo, logros, racha y, si hace falta, un plan nuevo.
 */
@Service
public class SessionService {

    private final WorldRegistry registry;
    private final SessionLogRepository logs;
    private final PlanRepository plans;
    private final PlanService planService;
    private final MetricRepository metrics;
    private final UserWorldRepository userWorlds;
    private final EngagementService engagement;
    private final WorldContextService contexts;
    private final CatalogRepository catalog;

    public SessionService(WorldRegistry registry, SessionLogRepository logs, PlanRepository plans,
            PlanService planService, MetricRepository metrics, UserWorldRepository userWorlds,
            EngagementService engagement, WorldContextService contexts, CatalogRepository catalog) {
        this.registry = registry;
        this.logs = logs;
        this.plans = plans;
        this.planService = planService;
        this.metrics = metrics;
        this.userWorlds = userWorlds;
        this.engagement = engagement;
        this.contexts = contexts;
        this.catalog = catalog;
    }

    public record BlockLogRequest(UUID planBlockId, String movementKey, Integer setIndex, Integer reps, Double loadKg,
            Integer rir, Integer holdS, Integer durationS, Double distanceM, Integer pain, Boolean completed) {
    }

    public record SessionLogRequest(UUID planSessionId, String title, LocalDate performedOn, Integer durationMin,
            Integer rpe, Integer painBefore, Integer painAfter, Integer moodBefore, Integer moodAfter, Double distanceM,
            String notes, List<BlockLogRequest> blocks) {
    }

    public record SessionResult(UUID sessionLogId, List<String> highlights, List<Achievement> achievements,
            Streak streak, List<Unlock> unlocks, boolean planRegenerated) {
    }

    public record Unlock(String progressionKey, String progressionName, int fromStep, int toStep, String stepName) {
    }

    @Transactional
    public SessionResult log(WorldContext ctx, SessionLogRequest req) {
        validate(req);
        WorldModule module = registry.require(ctx.world().worldKey());
        String kind = null;
        String title = req.title();
        Map<UUID, BlockRow> planned = new HashMap<>();
        if (req.planSessionId() != null) {
            UUID owner = plans.userWorldOfSession(req.planSessionId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesión del plan no encontrada"));
            if (!owner.equals(ctx.world().id())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esa sesión no es de tu plan");
            }
            var session = plans.session(req.planSessionId()).orElseThrow();
            kind = session.kind();
            if (title == null) title = session.title();
            UUID planId = plans.active(ctx.world().id()).map(PlanRepository.PlanRow::id).orElse(null);
            if (planId != null) {
                plans.blocksByPlan(planId).getOrDefault(req.planSessionId(), List.of())
                        .forEach(b -> planned.put(b.id(), b));
            }
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
        LocalDate day = req.performedOn() == null ? LocalDate.now(MetricRepository.ZONE) : req.performedOn();
        LoggedSession draft = new LoggedSession(null, req.planSessionId(), kind, day, req.durationMin(), req.rpe(),
                req.painBefore(), req.painAfter(), req.moodBefore(), req.moodAfter(), req.distanceM(), req.notes(),
                blocks);
        UUID logId = logs.insert(ctx.user().userId(), ctx.world().id(), title, draft);
        LoggedSession logged = new LoggedSession(logId, draft.planSessionId(), kind, day, draft.durationMin(),
                draft.rpe(), draft.painBefore(), draft.painAfter(), draft.moodBefore(), draft.moodAfter(),
                draft.distanceM(), draft.notes(), blocks);

        // Métricas comunes a todos los mundos.
        UUID uid = ctx.user().userId();
        UUID uw = ctx.world().id();
        String ref = logId.toString();
        java.time.OffsetDateTime at = MetricRepository.momentOf(day);
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

        boolean regenerated = false;
        if (outcome.regeneratePlan() || !unlocks.isEmpty()) {
            planService.regenerate(contexts.reload(ctx));
            regenerated = true;
        }
        return new SessionResult(logId, highlights, fresh, streak, unlocks, regenerated);
    }

    /**
     * Aplica efectos (métricas, escalones, estado, nivel). Lo usan sesiones y tests.
     * @return escalones que avanzaron
     */
    public List<Unlock> apply(WorldContext ctx, SessionOutcome outcome, String source, String ref) {
        return apply(ctx, outcome, source, ref, java.time.OffsetDateTime.now());
    }

    public List<Unlock> apply(WorldContext ctx, SessionOutcome outcome, String source, String ref,
            java.time.OffsetDateTime at) {
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

    public void nextDayPain(WorldContext ctx, UUID sessionLogId, int pain) {
        UUID owner = logs.ownerWorld(sessionLogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesión no encontrada"));
        if (!owner.equals(ctx.world().id())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No es tu sesión");
        if (pain < 0 || pain > 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El dolor va de 0 a 10");
        logs.setNextDayPain(sessionLogId, pain);
        metrics.insert(ctx.user().userId(), ctx.world().id(), "dolor_24h", pain, "sesion", sessionLogId.toString(), null);
        SessionOutcome outcome = registry.require(ctx.world().worldKey()).onNextDayPain(ctx, sessionLogId, pain);
        List<Unlock> changed = apply(ctx, outcome, "sesion", sessionLogId.toString());
        if (outcome.regeneratePlan() || !changed.isEmpty()) {
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
            planService.regenerate(contexts.reload(ctx));
        }
        return target;
    }

    private static void validate(SessionLogRequest r) {
        if (r.rpe() != null && (r.rpe() < 1 || r.rpe() > 10)) bad("El esfuerzo percibido va de 1 a 10");
        for (Integer p : new Integer[] { r.painBefore(), r.painAfter() }) {
            if (p != null && (p < 0 || p > 10)) bad("El dolor va de 0 a 10");
        }
        if (r.durationMin() != null && (r.durationMin() < 1 || r.durationMin() > 600)) bad("Duración no válida");
        if (r.blocks() != null && r.blocks().size() > 300) bad("Demasiados registros en una sesión");
    }

    private static void bad(String msg) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
