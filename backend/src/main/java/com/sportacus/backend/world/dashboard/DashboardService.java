package com.sportacus.backend.world.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.engagement.EngagementService;
import com.sportacus.backend.engagement.EngagementService.Achievement;
import com.sportacus.backend.engagement.EngagementService.Streak;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.profile.Profile360Service;
import com.sportacus.backend.world.catalog.CatalogModels.TrackInfo;
import com.sportacus.backend.world.catalog.CatalogModels.WorldInfo;
import com.sportacus.backend.world.catalog.CatalogRepository;
import com.sportacus.backend.world.core.PlanView;
import com.sportacus.backend.world.core.ProfileInsights.Readiness;
import com.sportacus.backend.world.core.Widget;
import com.sportacus.backend.world.core.WorldContext;
import com.sportacus.backend.world.core.WorldModule;
import com.sportacus.backend.world.core.WorldRegistry;
import com.sportacus.backend.world.discovery.DiscoveryService;
import com.sportacus.backend.world.enrollment.EnrollmentService;
import com.sportacus.backend.world.plan.PlanService;
import com.sportacus.backend.world.session.SessionLogRepository;
import com.sportacus.backend.world.session.SessionLogRepository.SessionSummary;

/**
 * Inicio y progreso de un mundo: el módulo decide los widgets; el núcleo añade avisos de seguridad, el check-in diario
 * y una pregunta sugerida del Perfil 360 (sin presión: una sola, que se puede omitir). En Mi Base añade además el
 * anillo del perfil y los mundos sugeridos.
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final WorldRegistry registry;
    private final PlanService planService;
    private final CatalogRepository catalog;
    private final EngagementService engagement;
    private final SessionLogRepository sessionLogs;
    private final Profile360Service profile360;
    private final DiscoveryService discovery;

    public DashboardService(WorldRegistry registry, PlanService planService, CatalogRepository catalog,
            EngagementService engagement, SessionLogRepository sessionLogs, Profile360Service profile360,
            DiscoveryService discovery) {
        this.registry = registry;
        this.planService = planService;
        this.catalog = catalog;
        this.engagement = engagement;
        this.sessionLogs = sessionLogs;
        this.profile360 = profile360;
        this.discovery = discovery;
    }

    public record WorldHeader(String key, String name, String icon, String color, String trackKey, String trackName,
            int level) {
    }

    public record DashboardResponse(WorldHeader world, String greeting, Streak streak, List<Widget> widgets) {
    }

    public record ProgressResponse(WorldHeader world, Streak streak, List<Widget> widgets,
            List<Achievement> achievements, List<SessionSummary> recentSessions) {
    }

    public record Suggestion(String key, String name, String icon, String color, String tagline, int score,
            List<String> reasons) {
    }

    public DashboardResponse dashboard(WorldContext ctx) {
        WorldModule module = registry.require(ctx.world().worldKey());
        PlanView plan = planService.ensurePlan(ctx);
        boolean base = EnrollmentService.BASE.equals(ctx.world().worldKey());
        List<Widget> widgets = new ArrayList<>(safetyNotices(ctx.user().health()));
        if (plan.updateReason() != null && plan.updatedAt() != null
                && plan.updatedAt().isAfter(java.time.OffsetDateTime.now().minusHours(24))) {
            widgets.add(Widget.notice("plan_actualizado", "success", "Tu plan está al día", plan.updateReason()));
        }
        widgets.add(daily(ctx));
        Widget question = question(ctx, base ? "home" : "world");
        if (base) {
            widgets.add(profileRing(ctx));
            if (question != null) widgets.add(question);
            widgets.addAll(module.dashboard(ctx, plan));
            Widget suggestions = suggestions(ctx);
            if (suggestions != null) widgets.add(suggestions);
        } else {
            widgets.addAll(module.dashboard(ctx, plan));
            if (question != null) widgets.add(question);
        }
        return new DashboardResponse(header(ctx), greeting(ctx.user().displayName()),
                engagement.streak(ctx.user().userId()), widgets);
    }

    public ProgressResponse progress(WorldContext ctx) {
        WorldModule module = registry.require(ctx.world().worldKey());
        return new ProgressResponse(header(ctx), engagement.streak(ctx.user().userId()), module.progress(ctx),
                engagement.achievements(ctx.user().userId(), ctx.world().worldKey()),
                sessionLogs.recent(ctx.world().id(), 10));
    }

    public WorldHeader header(WorldContext ctx) {
        WorldInfo w = catalog.world(ctx.world().worldKey()).orElseThrow();
        TrackInfo t = ctx.world().trackKey() == null ? null
                : catalog.track(w.key(), ctx.world().trackKey()).orElse(null);
        return new WorldHeader(w.key(), w.name(), w.icon(), w.color(), t == null ? null : t.key(),
                t == null ? null : t.name(), ctx.world().level());
    }

    /** Check-in de hoy: la invitación si falta, o la disposición resultante si ya se hizo. */
    private Widget daily(WorldContext ctx) {
        Readiness r = ctx.user().insights().readiness();
        if (r == null) return Widget.checkin(false, null);
        return Widget.readiness(r.factor(), r.label(), r.reasons());
    }

    private Widget question(WorldContext ctx, String context) {
        try {
            var items = profile360.next(ctx.user().userId(), 1, context);
            // El chequeo de salud ya tiene su propio aviso arriba: no se repite como tarjeta.
            items = items.stream().filter(i -> !"health".equals(i.kind())).toList();
            return items.isEmpty() ? null : Widget.question(items.get(0));
        } catch (RuntimeException e) {
            log.warn("No se pudo calcular la siguiente pregunta: {}", e.toString());
            return null;
        }
    }

    private Widget profileRing(WorldContext ctx) {
        var overview = profile360.overview(ctx.user().userId());
        List<Map<String, Object>> areas = new ArrayList<>();
        for (var a : overview.areas()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", a.key());
            m.put("name", a.name());
            m.put("icon", a.icon());
            m.put("pct", a.pct());
            areas.add(m);
        }
        var c = overview.completeness();
        return Widget.profileRing(c.pct(), c.level(), c.label(), areas);
    }

    private Widget suggestions(WorldContext ctx) {
        var ranking = discovery.rank(ctx.user(), Answers.empty());
        List<Suggestion> items = ranking.stream().limit(3).map(r -> new Suggestion(r.worldKey(), r.name(), r.icon(),
                r.color(), r.tagline(), r.score(), r.reasons())).toList();
        if (items.isEmpty()) return null;
        boolean knowsLittle = ctx.user().insights().facts().count() < 8;
        return Widget.worldSuggestions("Mundos para ti",
                knowsLittle ? "Mientras más nos cuentes, más precisa será esta sugerencia" : "Según lo que nos contaste",
                items);
    }

    /** Avisos que el núcleo impone en cualquier mundo según el chequeo de salud. */
    private List<Widget> safetyNotices(HealthSummary h) {
        List<Widget> out = new ArrayList<>();
        if (!h.screened()) {
            out.add(Widget.notice("salud_pendiente", "info", "Modo prudente",
                    "Mientras no hagas tu chequeo de salud (1 minuto), tus planes van a intensidad moderada y sin "
                            + "saltos. Hazlo cuando quieras desde tu perfil."));
        } else if (h.mustRefer()) {
            out.add(Widget.notice("derivar", "danger", "Consulta a un profesional antes de seguir",
                    "Marcaste señales que un médico debe revisar. Mientras tanto, evita el ejercicio intenso. "
                            + "Si ya tienes su aval, confírmalo en tu perfil de salud."));
        } else if (!h.vigorousAllowed()) {
            out.add(Widget.notice("aval", "warn", "Mantén intensidad moderada",
                    "Según tu chequeo, conviene el aval de un profesional antes de entrenar a intensidad alta."));
        }
        return out;
    }

    private static String greeting(String name) {
        int hour = java.time.LocalTime.now(com.sportacus.backend.world.metrics.MetricRepository.ZONE).getHour();
        String salute = hour < 12 ? "Buenos días" : hour < 19 ? "Buenas tardes" : "Buenas noches";
        return salute + ", " + name;
    }
}
