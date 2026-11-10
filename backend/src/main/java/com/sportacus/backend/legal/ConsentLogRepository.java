package com.sportacus.backend.legal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tabla {@code consent_log} (V5): prueba de cada autorización y revocación (Decreto 1377 de 2013, art. 7). Es
 * append-only: nunca se actualiza una fila, se agrega otra.
 */
@Repository
public class ConsentLogRepository {

    public static final String POLICY = "politica_datos";
    public static final String TERMS = "terminos";
    public static final String HEALTH_DATA = "datos_salud";
    public static final String GUARDIAN = "autorizacion_representante";

    private final JdbcTemplate jdbc;

    public ConsentLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(UUID userId, String document, String version, boolean granted, String source) {
        jdbc.update("INSERT INTO consent_log (user_id, document, version, granted, source) VALUES (?, ?, ?, ?, ?)",
                userId, document, version, granted, source);
    }

    /** Última versión aceptada (granted) de cada documento. */
    public Map<String, String> acceptedVersions(UUID userId) {
        Map<String, String> out = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON (document) document, version FROM consent_log
                WHERE user_id = ? AND granted AND version IS NOT NULL
                ORDER BY document, created_at DESC, id DESC
                """, rs -> {
            out.put(rs.getString(1), rs.getString(2));
        }, userId);
        return out;
    }

    /** Historial completo (para la exportación de datos). */
    public List<Map<String, Object>> history(UUID userId) {
        return jdbc.queryForList("""
                SELECT document, version, granted, source, created_at FROM consent_log
                WHERE user_id = ? ORDER BY created_at, id
                """, userId);
    }
}
