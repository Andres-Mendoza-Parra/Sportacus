package com.sportacus.backend.analytics;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.sportacus.backend.common.Json;

/**
 * Analítica de producto propia (docs/22): eventos en la tabla {@code event_log}, sin terceros. Nunca rompe el flujo
 * que la llama. Las propiedades NO deben llevar datos de salud ni texto libre del usuario: solo claves, conteos y
 * categorías (p. ej. {"world":"gym","minutes":45}).
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    public static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{2,59}");

    private final JdbcTemplate jdbc;
    private final Json json;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    public AnalyticsService(JdbcTemplate jdbc, Json json,
            org.springframework.transaction.PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.json = json;
        // Transacción propia: un fallo al guardar el evento nunca aborta la transacción del flujo que lo llama.
        this.tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Evento del servidor. */
    public void track(UUID userId, String name, Map<String, ?> props) {
        insert(userId, name, props, "server", null);
    }

    public void track(UUID userId, String name) {
        track(userId, name, Map.of());
    }

    /** Evento enviado por la app (POST /api/events). */
    public void trackFromApp(UUID userId, String name, Map<String, ?> props, OffsetDateTime occurredAt) {
        insert(userId, name, props, "app", occurredAt);
    }

    private void insert(UUID userId, String name, Map<String, ?> props, String source, OffsetDateTime at) {
        if (name == null || !NAME.matcher(name).matches()) return;
        try {
            tx.executeWithoutResult(s -> jdbc.update("""
                    INSERT INTO event_log (user_id, name, props, source, occurred_at)
                    VALUES (?, ?, ?::jsonb, ?, COALESCE(?::timestamptz, now()))
                    """, userId, name, json.write(props == null ? Map.of() : props), source, at));
        } catch (RuntimeException e) {
            log.debug("No se pudo registrar el evento {}: {}", name, e.toString());
        }
    }
}
