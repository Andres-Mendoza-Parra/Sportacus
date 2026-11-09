package com.sportacus.backend.world.plan;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.world.catalog.CatalogModels.MovementInfo;
import com.sportacus.backend.world.catalog.CatalogModels.WorldMovement;
import com.sportacus.backend.world.catalog.CatalogRepository;
import com.sportacus.backend.world.catalog.SafetyFilter;
import com.sportacus.backend.world.core.GeneratedPlan;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedBlock;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedSession;
import com.sportacus.backend.world.core.PlanView;
import com.sportacus.backend.world.core.PlanView.BlockView;
import com.sportacus.backend.world.core.PlanView.SessionView;
import com.sportacus.backend.world.core.WorldContext;
import com.sportacus.backend.world.core.WorldRegistry;
import com.sportacus.backend.world.metrics.MetricRepository;
import com.sportacus.backend.world.plan.PlanRepository.BlockRow;
import com.sportacus.backend.world.plan.PlanRepository.PlanRow;
import com.sportacus.backend.world.plan.PlanRepository.SessionRow;
import com.sportacus.backend.world.session.SessionLogRepository;

/**
 * Genera, guarda y presenta el plan del mundo. El núcleo aplica SIEMPRE el filtro de seguridad a
 * lo que devuelva el módulo: aunque un mundo se equivoque, un movimiento contraindicado no llega al usuario.
 */
@Service
public class PlanService {

    public static final String ENGINE_VERSION = "mundos-1";
    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    private final WorldRegistry registry;
    private final PlanRepository plans;
    private final SessionLogRepository sessionLogs;
    private final CatalogRepository catalog;
    private final SafetyFilter safety;
    private final JdbcTemplate jdbc;

    public PlanService(WorldRegistry registry, PlanRepository plans, SessionLogRepository sessionLogs,
            CatalogRepository catalog, SafetyFilter safety, JdbcTemplate jdbc) {
        this.registry = registry;
        this.plans = plans;
        this.sessionLogs = sessionLogs;
        this.catalog = catalog;
        this.safety = safety;
        this.jdbc = jdbc;
    }

    /** Plan activo; si no existe, lo genera. */
    public PlanView ensurePlan(WorldContext ctx) {
        return plans.active(ctx.world().id()).map(p -> view(ctx, p)).orElseGet(() -> regenerate(ctx));
    }

    @Transactional
    public PlanView regenerate(WorldContext ctx) {
        GeneratedPlan generated = registry.require(ctx.world().worldKey()).generatePlan(ctx);
        GeneratedPlan safe = sanitize(ctx, generated);
        plans.replaceActive(ctx.world().id(), ctx.world().trackKey(), safe, ENGINE_VERSION);
        return view(ctx, plans.active(ctx.world().id()).orElseThrow());
    }

    public SessionView session(WorldContext ctx, UUID sessionId) {
        PlanView plan = ensurePlan(ctx);
        return plan.sessions().stream().filter(s -> s.id().equals(sessionId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesión no encontrada en tu plan"));
    }

    /** Cambia el movimiento de un bloque por otro equivalente (mismo patrón, nivel parecido, seguro). */
    @Transactional
    public BlockView swap(WorldContext ctx, UUID blockId) {
        UUID owner = plans.userWorldOfBlock(blockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bloque no encontrado"));
        if (!owner.equals(ctx.world().id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ese bloque no es de tu plan");
        }
        BlockRow block = plans.block(blockId).orElseThrow();
        if (block.movementKey() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este bloque no tiene un ejercicio que cambiar");
        }
        if (block.progressionKey() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Este ejercicio es un escalón de tu progresión: avanza o retrocede el escalón en lugar de cambiarlo");
        }
        String world = ctx.world().worldKey();
        WorldMovement current = catalog.worldMovement(world, block.movementKey()).orElse(null);
        Set<String> available = SafetyFilter.availableEquipment(ctx.user().profile());
        Set<String> inSession = new HashSet<>();
        plans.blocksByPlan(plans.active(ctx.world().id()).orElseThrow().id()).getOrDefault(block.sessionId(), List.of())
                .forEach(b -> inSession.add(b.movementKey()));
        List<WorldMovement> candidates = safety.filter(catalog.movements(world), ctx.user()).stream()
                .filter(m -> !inSession.contains(m.key()))
                .filter(m -> SafetyFilter.hasEquipment(m.movement(), available))
                .filter(m -> current == null || (current.pattern() != null ? current.pattern().equals(m.pattern())
                        : current.role().equals(m.role())))
                .filter(m -> current == null || Math.abs(m.level() - current.level()) <= 1)
                .toList();
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No hay una alternativa segura para este ejercicio");
        }
        WorldMovement pick = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        plans.swapMovement(blockId, pick.key(), pick.name());
        jdbc.update("INSERT INTO movement_feedback (user_id, world_key, movement_key, reaction) VALUES (?, ?, ?, 'swap')",
                ctx.user().userId(), world, block.movementKey());
        BlockRow updated = plans.block(blockId).orElseThrow();
        return toView(updated);
    }

    // ── Presentación ──────────────────────────────────────────

    public PlanView view(WorldContext ctx, PlanRow p) {
        // Semanas de calendario (lunes a domingo) ancladas al lunes de la semana en que empezó el plan: así, si el
        // plan se regenera a mitad de semana, lo hecho desde el lunes sigue contando.
        LocalDate today = LocalDate.now(MetricRepository.ZONE);
        LocalDate anchor = p.startsOn().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        long days = Math.max(0, ChronoUnit.DAYS.between(anchor, today));
        int week = (int) (days / 7);
        int currentWeek = p.weeks() <= 1 ? 0 : Math.min(p.weeks() - 1, week);
        LocalDate weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        Map<String, LocalDate> done = sessionLogs.doneSince(ctx.world().id(), weekStart);
        Map<UUID, List<BlockRow>> blocks = plans.blocksByPlan(p.id());
        List<SessionView> sessions = new ArrayList<>();
        for (SessionRow s : plans.sessions(p.id())) {
            boolean isCurrent = s.weekIndex() == currentWeek;
            LocalDate doneOn = done.get(s.weekIndex() + ":" + s.dayIndex());
            sessions.add(new SessionView(s.id(), s.weekIndex(), s.dayIndex(), s.weekday(), s.title(), s.focus(),
                    s.kind(), s.estMinutes(), s.notes(), isCurrent && doneOn != null, doneOn,
                    blocks.getOrDefault(s.id(), List.of()).stream().map(this::toView).toList()));
        }
        return new PlanView(p.id(), p.title(), p.summary(), p.weeks(), currentWeek, p.startsOn(), weekStart, p.score(),
                p.rationale(), sessions);
    }

    private BlockView toView(BlockRow b) {
        MovementInfo m = catalog.movement(b.movementKey()).orElse(null);
        return new BlockView(b.id(), b.orderIndex(), b.blockType(), b.movementKey(), m == null ? null : m.name(),
                b.title(), b.prescription(), b.progressionKey(), b.stepIndex(), b.notes(),
                m == null ? null : m.firstImage());
    }

    private GeneratedPlan sanitize(WorldContext ctx, GeneratedPlan plan) {
        List<GeneratedSession> sessions = new ArrayList<>();
        for (GeneratedSession s : plan.sessions()) {
            List<GeneratedBlock> blocks = new ArrayList<>();
            for (GeneratedBlock b : s.blocks()) {
                if (b.movementKey() == null) {
                    blocks.add(b);
                    continue;
                }
                MovementInfo m = catalog.movement(b.movementKey()).orElse(null);
                if (m == null) {
                    log.warn("Mundo {} generó un movimiento inexistente: {}", ctx.world().worldKey(), b.movementKey());
                } else if (!safety.allowed(m, ctx.user())) {
                    log.info("Filtro de seguridad retiró {} del plan de {}", m.key(), ctx.user().userId());
                } else {
                    blocks.add(b);
                }
            }
            if (!blocks.isEmpty()) {
                sessions.add(new GeneratedSession(s.weekIndex(), s.dayIndex(), s.weekday(), s.title(), s.focus(),
                        s.kind(), s.estMinutes(), s.notes(), blocks));
            }
        }
        return new GeneratedPlan(plan.title(), plan.summary(), plan.weeks(), plan.score(), plan.rationale(), sessions);
    }
}
