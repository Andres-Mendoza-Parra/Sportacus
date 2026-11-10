package com.sportacus.backend.world.assessment;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.engagement.EngagementService;
import com.sportacus.backend.world.catalog.CatalogModels.AssessmentInfo;
import com.sportacus.backend.world.catalog.CatalogRepository;
import com.sportacus.backend.world.core.AssessmentOutcome;
import com.sportacus.backend.world.core.UserProfileData;
import com.sportacus.backend.world.core.WorldContext;
import com.sportacus.backend.world.core.WorldRegistry;
import com.sportacus.backend.world.metrics.MetricRepository;
import com.sportacus.backend.world.plan.PlanService;
import com.sportacus.backend.world.session.SessionService;
import com.sportacus.backend.world.session.SessionService.Unlock;

/** Tests físicos definidos por cada mundo (contenido) con clasificación por normas. */
@Service
public class AssessmentService {

    private final CatalogRepository catalog;
    private final MetricRepository metrics;
    private final WorldRegistry registry;
    private final SessionService sessions;
    private final EngagementService engagement;
    private final PlanService planService;
    private final com.sportacus.backend.world.enrollment.WorldContextService contexts;
    private final com.sportacus.backend.world.goals.GoalService goals;
    private final com.sportacus.backend.analytics.AnalyticsService analytics;

    public AssessmentService(CatalogRepository catalog, MetricRepository metrics, WorldRegistry registry,
            SessionService sessions, EngagementService engagement, PlanService planService,
            com.sportacus.backend.world.enrollment.WorldContextService contexts,
            com.sportacus.backend.world.goals.GoalService goals,
            com.sportacus.backend.analytics.AnalyticsService analytics) {
        this.goals = goals;
        this.analytics = analytics;
        this.contexts = contexts;
        this.catalog = catalog;
        this.metrics = metrics;
        this.registry = registry;
        this.sessions = sessions;
        this.engagement = engagement;
        this.planService = planService;
    }

    public record AssessmentView(AssessmentInfo definition, Double lastValue, OffsetDateTime lastAt,
            String lastClassification, boolean due, Integer daysUntilDue, String sourceName, String sourceUrl) {
    }

    public record AssessmentResult(String key, double value, String unit, String classification,
            List<String> highlights, List<Unlock> unlocks, boolean firstTime) {
    }

    public List<AssessmentView> list(WorldContext ctx) {
        List<AssessmentView> out = new ArrayList<>();
        var sources = catalog.sources();
        for (AssessmentInfo a : catalog.assessments(ctx.world().worldKey())) {
            Double last = metrics.latest(ctx.world().id(), a.metricKey()).orElse(null);
            OffsetDateTime at = metrics.latestAt(ctx.world().id(), a.metricKey()).orElse(null);
            Integer daysUntil = null;
            boolean due = true;
            if (at != null) {
                long since = ChronoUnit.DAYS.between(at.atZoneSameInstant(MetricRepository.ZONE).toLocalDate(),
                        LocalDate.now(MetricRepository.ZONE));
                daysUntil = (int) Math.max(0, a.retestDays() - since);
                due = since >= a.retestDays();
            }
            var src = sources.stream().filter(s -> s.key().equals(a.sourceKey())).findFirst().orElse(null);
            out.add(new AssessmentView(a, last, at, last == null ? null : classify(a, last, ctx.user().profile()), due,
                    daysUntil, src == null ? null : src.name(), src == null ? null : src.url()));
        }
        return out;
    }

    @Transactional
    public AssessmentResult record(WorldContext ctx, String key, double value) {
        AssessmentInfo a = catalog.assessments(ctx.world().worldKey()).stream().filter(x -> x.key().equals(key))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ese test no pertenece a tu mundo activo"));
        if (value < 0 || value > 100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor fuera de rango");
        boolean first = metrics.count(ctx.world().id(), a.metricKey()) == 0;
        metrics.insert(ctx.user().userId(), ctx.world().id(), a.metricKey(), value, "test", key, Map.of("assessment", key));
        String classification = classify(a, value, ctx.user().profile());
        AssessmentOutcome outcome = registry.require(ctx.world().worldKey()).onAssessment(ctx, key, value);
        List<Unlock> unlocks = sessions.apply(ctx, outcome.effects(), "test", key);
        List<String> highlights = new ArrayList<>(outcome.highlights());
        engagement.grant(ctx.user().userId(), null, "primer_test", "Primer test físico", "📏");
        if (outcome.effects() != null) {
            for (var g : outcome.effects().achievements()) {
                if (engagement.grant(ctx.user().userId(), ctx.world().worldKey(), g.key(), g.title(), g.icon())) {
                    highlights.add(g.icon() + " Logro: " + g.title());
                }
            }
            highlights.addAll(outcome.effects().highlights());
        }
        if (outcome.effects() != null && (outcome.effects().regeneratePlan() || !unlocks.isEmpty())) {
            planService.regenerate(contexts.reload(ctx)); // con el estado recién fusionado
        }
        for (var goal : goals.check(ctx.world().id())) {
            highlights.add(com.sportacus.backend.world.goals.GoalService.ICON + " ¡Meta lograda! " + goal.label());
        }
        java.util.UUID uid = ctx.user().userId();
        String world = ctx.world().worldKey();
        com.sportacus.backend.world.goals.AfterCommit.run(
                () -> analytics.track(uid, "assessment_recorded", Map.of("world", world)));
        return new AssessmentResult(key, value, a.unit(),
                outcome.classification() != null ? outcome.classification() : classification, highlights, unlocks, first);
    }

    /**
     * Clasificación según {@code norms}:
     * {"bands":[{"min":0,"max":10,"label":"…"}]} o, por edad y sexo,
     * {"groups":[{"sex":"femenino","ageMin":60,"ageMax":64,"bands":[…]}]}. "min" inclusivo, "max" exclusivo.
     */
    @SuppressWarnings("unchecked")
    public static String classify(AssessmentInfo a, double value, UserProfileData profile) {
        Map<String, Object> norms = a.norms();
        if (norms == null || norms.isEmpty()) return null;
        List<Map<String, Object>> bands = null;
        if (norms.get("groups") instanceof List<?> groups) {
            int age = profile.ageOr(-1);
            for (Object g : groups) {
                Map<String, Object> gm = (Map<String, Object>) g;
                Object sex = gm.get("sex");
                if (sex != null && !sex.equals(profile.sex())) continue;
                double min = num(gm.get("ageMin"), 0);
                double max = num(gm.get("ageMax"), 200);
                boolean ageBound = gm.containsKey("ageMin") || gm.containsKey("ageMax");
                if (age < 0 ? ageBound : (age < min || age > max)) continue; // edad desconocida: sin normas por edad
                bands = (List<Map<String, Object>>) gm.get("bands");
                break;
            }
        }
        if (bands == null && norms.get("bands") instanceof List<?> b) bands = (List<Map<String, Object>>) b;
        if (bands == null) return null;
        for (Map<String, Object> band : bands) {
            double min = num(band.get("min"), Double.NEGATIVE_INFINITY);
            double max = num(band.get("max"), Double.POSITIVE_INFINITY);
            if (value >= min && value < max) return String.valueOf(band.get("label"));
        }
        return null;
    }

    private static double num(Object o, double fallback) {
        return o instanceof Number n ? n.doubleValue() : fallback;
    }
}
