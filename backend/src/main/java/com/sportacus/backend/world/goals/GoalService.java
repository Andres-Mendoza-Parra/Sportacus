package com.sportacus.backend.world.goals;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.analytics.AnalyticsService;
import com.sportacus.backend.engagement.EngagementService;
import com.sportacus.backend.world.catalog.CatalogRepository;
import com.sportacus.backend.world.goals.GoalProgress.Result;
import com.sportacus.backend.world.goals.GoalProgress.Sample;
import com.sportacus.backend.world.goals.GoalProgress.SessionFact;
import com.sportacus.backend.world.goals.GoalRepository.GoalRow;
import com.sportacus.backend.world.metrics.MetricRepository;

/**
 * Metas de cada mundo (auditoría A20): avance medible, logro automático cuando el objetivo se puede medir y
 * marcado manual ("Marcar como lograda" / "Reabrir") para todas.
 */
@Service
public class GoalService {

    public static final String ICON = "🎯";

    private final GoalRepository goals;
    private final CatalogRepository catalog;
    private final EngagementService engagement;
    private final AnalyticsService analytics;

    public GoalService(GoalRepository goals, CatalogRepository catalog, EngagementService engagement,
            AnalyticsService analytics) {
        this.goals = goals;
        this.catalog = catalog;
        this.engagement = engagement;
        this.analytics = analytics;
    }

    /**
     * @param auto       Sportacus mide el avance y la marca como lograda sola al cumplirse
     * @param achievedBy "auto" o "manual" cuando está lograda
     */
    public record GoalView(UUID id, String worldKey, String goalKey, String label, Map<String, Object> target,
            LocalDate targetDate, String status, OffsetDateTime achievedAt, Integer progressPct, String progressText,
            boolean auto, String achievedBy) {
    }

    /** Metas del mundo con su avance. Las medibles que ya se cumplieron se marcan como logradas en el acto. */
    @Transactional
    public List<GoalView> list(UUID userWorldId) {
        check(userWorldId);
        Data data = new Data(userWorldId);
        return goals.byWorld(userWorldId).stream().map(g -> view(g, evaluate(g, data))).toList();
    }

    /**
     * Revisa las metas activas del mundo tras registrar una sesión o un test.
     *
     * @return las que se acaban de lograr
     */
    @Transactional
    public List<GoalView> check(UUID userWorldId) {
        Data data = new Data(userWorldId);
        List<GoalView> fresh = new ArrayList<>();
        for (GoalRow g : goals.byWorld(userWorldId)) {
            if (!"activa".equals(g.status())) continue;
            Result r = evaluate(g, data);
            if (r.measurable() && r.reached() && goals.achieve(g.id(), "auto")) {
                celebrate(g, true);
                fresh.add(view(goals.byId(g.id()).orElseThrow(), r));
            }
        }
        return fresh;
    }

    /** Fija el punto de partida de las metas nuevas (escalones o marcas al inscribirse). */
    @Transactional
    public void initBaselines(UUID userWorldId) {
        Data data = new Data(userWorldId);
        goals.byWorld(userWorldId).forEach(g -> evaluate(g, data));
    }

    @Transactional
    public GoalView complete(UUID userId, UUID goalId) {
        GoalRow g = owned(userId, goalId);
        if ("abandonada".equals(g.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esa meta ya no está vigente");
        }
        if (goals.achieve(goalId, "manual")) celebrate(g, false);
        GoalRow now = goals.byId(goalId).orElseThrow();
        return view(now, evaluate(now, new Data(now.userWorldId())));
    }

    @Transactional
    public GoalView reopen(UUID userId, UUID goalId) {
        GoalRow g = owned(userId, goalId);
        if ("abandonada".equals(g.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esa meta ya no está vigente");
        }
        goals.reopen(goalId);
        GoalRow now = goals.byId(goalId).orElseThrow();
        return view(now, evaluate(now, new Data(now.userWorldId())));
    }

    // ── Internos ──────────────────────────────────────────────

    private GoalRow owned(UUID userId, UUID goalId) {
        return goals.byId(goalId).filter(g -> g.userId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meta no encontrada"));
    }

    private Result evaluate(GoalRow g, Data data) {
        Result r;
        try {
            r = GoalProgress.evaluate(g.target(), g.createdAt(), g.baseline(), data, LocalDate.now(MetricRepository.ZONE));
        } catch (RuntimeException e) {
            return new Result(false, null, null, false, null); // un objetivo raro nunca rompe la vista
        }
        if (g.baseline() == null && r.baseline() != null) goals.setBaseline(g.id(), r.baseline());
        return r;
    }

    private void celebrate(GoalRow g, boolean auto) {
        String title = "Meta lograda: " + g.label();
        if (title.length() > 120) title = title.substring(0, 117) + "…";
        engagement.grant(g.userId(), g.worldKey(), achievementKey(g.goalKey()), title, ICON);
        UUID uid = g.userId();
        String world = g.worldKey();
        AfterCommit.run(() -> analytics.track(uid, "goal_completed", Map.of("world", world, "auto", auto)));
    }

    public static String achievementKey(String goalKey) {
        String k = "meta_" + goalKey;
        return k.length() > 80 ? k.substring(0, 80) : k;
    }

    private static GoalView view(GoalRow g, Result r) {
        boolean done = "lograda".equals(g.status());
        Integer pct = r.measurable() ? (done ? Integer.valueOf(100) : r.pct()) : (done ? Integer.valueOf(100) : null);
        return new GoalView(g.id(), g.worldKey(), g.goalKey(), g.label(), g.target(), g.targetDate(), g.status(),
                g.achievedAt(), pct, r.text(), r.measurable(), g.achievedBy());
    }

    /** Carga perezosa y memorizada de lo que piden las metas de un mundo. */
    private final class Data implements GoalProgress.Data {
        private final UUID uw;
        private final Map<String, List<Sample>> samples = new HashMap<>();
        private final Map<String, Integer> steps = new HashMap<>();
        private List<SessionFact> sessions;
        private Integer sum;

        Data(UUID uw) {
            this.uw = uw;
        }

        @Override
        public int bestStep(String progressionKey) {
            return steps.computeIfAbsent(progressionKey, k -> goals.bestStep(uw, k));
        }

        @Override
        public int sumSteps() {
            if (sum == null) sum = goals.sumSteps(uw);
            return sum;
        }

        @Override
        public List<Sample> metric(String metricKey) {
            return samples.computeIfAbsent(metricKey, k -> goals.samples(uw, k));
        }

        @Override
        public String direction(String metricKey) {
            return catalog.metric(metricKey).map(m -> m.direction()).orElse("up");
        }

        @Override
        public List<SessionFact> sessions() {
            if (sessions == null) sessions = goals.sessions(uw, LocalDate.now(MetricRepository.ZONE).minusDays(400));
            return sessions;
        }
    }
}
