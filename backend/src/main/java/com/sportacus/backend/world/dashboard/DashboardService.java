package com.sportacus.backend.world.dashboard;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.sportacus.backend.engagement.EngagementService;
import com.sportacus.backend.engagement.EngagementService.Achievement;
import com.sportacus.backend.engagement.EngagementService.Streak;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.catalog.CatalogModels.TrackInfo;
import com.sportacus.backend.world.catalog.CatalogModels.WorldInfo;
import com.sportacus.backend.world.catalog.CatalogRepository;
import com.sportacus.backend.world.core.PlanView;
import com.sportacus.backend.world.core.Widget;
import com.sportacus.backend.world.core.WorldContext;
import com.sportacus.backend.world.core.WorldModule;
import com.sportacus.backend.world.core.WorldRegistry;
import com.sportacus.backend.world.plan.PlanService;
import com.sportacus.backend.world.session.SessionLogRepository;
import com.sportacus.backend.world.session.SessionLogRepository.SessionSummary;

/** Inicio y progreso de un mundo: el módulo decide los widgets; el núcleo añade avisos de seguridad. */
@Service
public class DashboardService {

    private final WorldRegistry registry;
    private final PlanService planService;
    private final CatalogRepository catalog;
    private final EngagementService engagement;
    private final SessionLogRepository sessionLogs;

    public DashboardService(WorldRegistry registry, PlanService planService, CatalogRepository catalog,
            EngagementService engagement, SessionLogRepository sessionLogs) {
        this.registry = registry;
        this.planService = planService;
        this.catalog = catalog;
        this.engagement = engagement;
        this.sessionLogs = sessionLogs;
    }

    public record WorldHeader(String key, String name, String icon, String color, String trackKey, String trackName,
            int level) {
    }

    public record DashboardResponse(WorldHeader world, String greeting, Streak streak, List<Widget> widgets) {
    }

    public record ProgressResponse(WorldHeader world, Streak streak, List<Widget> widgets,
            List<Achievement> achievements, List<SessionSummary> recentSessions) {
    }

    public DashboardResponse dashboard(WorldContext ctx) {
        WorldModule module = registry.require(ctx.world().worldKey());
        PlanView plan = planService.ensurePlan(ctx);
        List<Widget> widgets = new ArrayList<>(safetyNotices(ctx.user().health()));
        widgets.addAll(module.dashboard(ctx, plan));
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

    /** Avisos que el núcleo impone en cualquier mundo según el chequeo de salud. */
    private List<Widget> safetyNotices(HealthSummary h) {
        List<Widget> out = new ArrayList<>();
        if (!h.screened()) {
            out.add(Widget.notice("salud_pendiente", "info", "Completa tu chequeo de salud",
                    "Toma 1 minuto y nos permite cuidarte mejor."));
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
