package com.sportacus.backend.analytics;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.user.User;

/** Analítica propia (sin terceros): eventos de la app y resumen para el equipo. */
@RestController
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final AnalyticsSummaryService summary;

    public AnalyticsController(AnalyticsService analytics, AnalyticsSummaryService summary) {
        this.analytics = analytics;
        this.summary = summary;
    }

    public record AppEvent(String name, Map<String, Object> props, String at) {
    }

    public record EventBatch(List<AppEvent> events) {
    }

    /** Lote de eventos de la app (máx. 50). Los eventos con nombre inválido se descartan sin romper el lote. */
    @PostMapping("/api/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void events(@AuthenticationPrincipal User user, @RequestBody EventBatch body) {
        if (body == null || body.events() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan los eventos");
        }
        if (body.events().size() > EventSanitizer.MAX_EVENTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Máximo " + EventSanitizer.MAX_EVENTS + " eventos por envío");
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (AppEvent e : body.events()) {
            if (e == null || !EventSanitizer.validName(e.name())) continue;
            analytics.trackFromApp(user.getId(), e.name(), EventSanitizer.props(e.props()), EventSanitizer.at(e.at(), now));
        }
    }

    /** Solo para los correos de {@code sportacus.admin-emails}; el resto recibe 403. */
    @GetMapping("/api/admin/analytics/summary")
    public AnalyticsSummaryService.Summary summary(@AuthenticationPrincipal User user) {
        if (!summary.isAdmin(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a esta sección");
        }
        return summary.summary();
    }
}
