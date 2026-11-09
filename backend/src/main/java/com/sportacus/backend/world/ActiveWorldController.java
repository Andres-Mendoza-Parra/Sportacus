package com.sportacus.backend.world;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.user.User;
import com.sportacus.backend.world.assessment.AssessmentService;
import com.sportacus.backend.world.assessment.AssessmentService.AssessmentResult;
import com.sportacus.backend.world.assessment.AssessmentService.AssessmentView;
import com.sportacus.backend.world.core.PlanView;
import com.sportacus.backend.world.core.PlanView.BlockView;
import com.sportacus.backend.world.core.PlanView.SessionView;
import com.sportacus.backend.world.core.SessionUi;
import com.sportacus.backend.world.core.WorldContext;
import com.sportacus.backend.world.core.WorldRegistry;
import com.sportacus.backend.world.dashboard.DashboardService;
import com.sportacus.backend.world.dashboard.DashboardService.DashboardResponse;
import com.sportacus.backend.world.dashboard.DashboardService.ProgressResponse;
import com.sportacus.backend.world.enrollment.WorldContextService;
import com.sportacus.backend.world.library.MovementService;
import com.sportacus.backend.world.library.MovementService.MovementCard;
import com.sportacus.backend.world.library.MovementService.MovementDetail;
import com.sportacus.backend.world.plan.PlanService;
import com.sportacus.backend.world.session.SessionLogRepository;
import com.sportacus.backend.world.session.SessionService;
import com.sportacus.backend.world.session.SessionService.SessionLogRequest;
import com.sportacus.backend.world.session.SessionService.SessionResult;

/**
 * Todo lo del mundo activo: inicio, plan, sesiones, progreso, tests y biblioteca. Cada endpoint
 * acepta {@code ?world=clave} para operar sobre otro mundo inscrito sin cambiar el activo.
 */
@RestController
@RequestMapping("/api/world")
public class ActiveWorldController {

    private final WorldContextService contexts;
    private final DashboardService dashboards;
    private final PlanService plans;
    private final SessionService sessions;
    private final SessionLogRepository sessionLogs;
    private final AssessmentService assessments;
    private final MovementService movements;
    private final WorldRegistry registry;

    public ActiveWorldController(WorldContextService contexts, DashboardService dashboards, PlanService plans,
            SessionService sessions, SessionLogRepository sessionLogs, AssessmentService assessments,
            MovementService movements, WorldRegistry registry) {
        this.contexts = contexts;
        this.dashboards = dashboards;
        this.plans = plans;
        this.sessions = sessions;
        this.sessionLogs = sessionLogs;
        this.assessments = assessments;
        this.movements = movements;
        this.registry = registry;
    }

    private WorldContext ctx(User user, String world) {
        return contexts.resolve(user, world);
    }

    // ── Inicio y progreso ─────────────────────────────────────

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal User user, @RequestParam(required = false) String world) {
        return dashboards.dashboard(ctx(user, world));
    }

    @GetMapping("/progress")
    public ProgressResponse progress(@AuthenticationPrincipal User user, @RequestParam(required = false) String world) {
        return dashboards.progress(ctx(user, world));
    }

    // ── Plan ──────────────────────────────────────────────────

    @GetMapping("/plan")
    public PlanView plan(@AuthenticationPrincipal User user, @RequestParam(required = false) String world) {
        return plans.ensurePlan(ctx(user, world));
    }

    @PostMapping("/plan/regenerate")
    public PlanView regenerate(@AuthenticationPrincipal User user, @RequestParam(required = false) String world) {
        return plans.regenerate(ctx(user, world));
    }

    public record SessionDetail(SessionView session, SessionUi ui) {
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionDetail session(@AuthenticationPrincipal User user, @PathVariable UUID sessionId,
            @RequestParam(required = false) String world) {
        WorldContext c = ctx(user, world);
        return new SessionDetail(plans.session(c, sessionId), registry.require(c.world().worldKey()).sessionUi());
    }

    @GetMapping("/session-ui")
    public SessionUi sessionUi(@AuthenticationPrincipal User user, @RequestParam(required = false) String world) {
        return registry.require(ctx(user, world).world().worldKey()).sessionUi();
    }

    @PostMapping("/blocks/{blockId}/swap")
    public BlockView swap(@AuthenticationPrincipal User user, @PathVariable UUID blockId,
            @RequestParam(required = false) String world) {
        return plans.swap(ctx(user, world), blockId);
    }

    // ── Registro ──────────────────────────────────────────────

    @PostMapping("/sessions/log")
    public SessionResult log(@AuthenticationPrincipal User user, @RequestBody SessionLogRequest request,
            @RequestParam(required = false) String world) {
        return sessions.log(ctx(user, world), request);
    }

    @GetMapping("/sessions/history")
    public List<SessionLogRepository.SessionSummary> history(@AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) String world) {
        return sessionLogs.recent(ctx(user, world).world().id(), Math.max(1, Math.min(100, limit)));
    }

    public record PainRequest(Integer pain) {
    }

    @PostMapping("/sessions/{logId}/next-day-pain")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void nextDayPain(@AuthenticationPrincipal User user, @PathVariable UUID logId,
            @RequestBody PainRequest body, @RequestParam(required = false) String world) {
        if (body == null || body.pain() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el dolor");
        sessions.nextDayPain(ctx(user, world), logId, body.pain());
    }

    public record StepRequest(Integer step) {
    }

    /** Subir o bajar un escalón de una progresión (un paso a la vez). Devuelve el plan regenerado. */
    @PostMapping("/progressions/{key}/step")
    public PlanView adjustStep(@AuthenticationPrincipal User user, @PathVariable String key,
            @RequestBody StepRequest body, @RequestParam(required = false) String world) {
        if (body == null || body.step() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el escalón");
        WorldContext c = ctx(user, world);
        sessions.adjustStep(c, key, body.step());
        return plans.ensurePlan(contexts.reload(c));
    }

    // ── Tests ─────────────────────────────────────────────────

    @GetMapping("/assessments")
    public List<AssessmentView> assessments(@AuthenticationPrincipal User user,
            @RequestParam(required = false) String world) {
        return assessments.list(ctx(user, world));
    }

    public record AssessmentRequest(Double value) {
    }

    @PostMapping("/assessments/{key}")
    public AssessmentResult record(@AuthenticationPrincipal User user, @PathVariable String key,
            @RequestBody AssessmentRequest body, @RequestParam(required = false) String world) {
        if (body == null || body.value() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el resultado");
        return assessments.record(ctx(user, world), key, body.value());
    }

    // ── Biblioteca ────────────────────────────────────────────

    @GetMapping("/movements")
    public Map<String, Object> library(@AuthenticationPrincipal User user, @RequestParam(required = false) String q,
            @RequestParam(required = false) String pattern, @RequestParam(defaultValue = "false") boolean favorites,
            @RequestParam(required = false) String world) {
        WorldContext c = ctx(user, world);
        List<MovementCard> items = movements.library(c, q, pattern, favorites);
        return Map.of("items", items, "patterns", movements.patterns(c));
    }

    @GetMapping("/movements/{key}")
    public MovementDetail movement(@AuthenticationPrincipal User user, @PathVariable String key,
            @RequestParam(required = false) String world) {
        return movements.detail(ctx(user, world), key);
    }

    @PostMapping("/movements/{key}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void favorite(@AuthenticationPrincipal User user, @PathVariable String key) {
        movements.setFavorite(user.getId(), key, true);
    }

    @DeleteMapping("/movements/{key}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfavorite(@AuthenticationPrincipal User user, @PathVariable String key) {
        movements.setFavorite(user.getId(), key, false);
    }

    public record FeedbackRequest(String reaction) {
    }

    @PostMapping("/movements/{key}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void feedback(@AuthenticationPrincipal User user, @PathVariable String key, @RequestBody FeedbackRequest body,
            @RequestParam(required = false) String world) {
        movements.feedback(ctx(user, world), key, body == null ? null : body.reaction());
    }
}
