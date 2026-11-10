package com.sportacus.backend.world.metrics;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.sportacus.backend.common.Json;
import com.sportacus.backend.world.core.Widget.Bar;
import com.sportacus.backend.world.core.Widget.Point;

/**
 * Serie temporal única (metric_entry). Cada mundo escribe aquí sus métricas al cerrar una sesión o
 * un test, y sus dashboards leen solo de aquí: así todos los gráficos usan la misma maquinaria.
 */
@Repository
public class MetricRepository {

    public static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private final JdbcTemplate jdbc;
    private final Json json;

    public MetricRepository(JdbcTemplate jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void insert(UUID userId, UUID userWorldId, String metricKey, double value, String source, String sourceRef,
            Map<String, Object> context) {
        insert(userId, userWorldId, metricKey, value, source, sourceRef, context, OffsetDateTime.now());
    }

    /** @param recordedAt momento al que pertenece el valor (p. ej. la fecha de una sesión registrada después) */
    public void insert(UUID userId, UUID userWorldId, String metricKey, double value, String source, String sourceRef,
            Map<String, Object> context, OffsetDateTime recordedAt) {
        jdbc.update("""
                INSERT INTO metric_entry (user_id, user_world_id, metric_key, value, source, source_ref, context, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """, userId, userWorldId, metricKey, value, source, sourceRef,
                json.write(context == null ? Map.of() : context), recordedAt);
    }

    /** Momento que se asigna a las métricas de una sesión: ahora si fue hoy, mediodía de ese día si fue antes. */
    public static OffsetDateTime momentOf(LocalDate performedOn) {
        LocalDate today = LocalDate.now(ZONE);
        if (performedOn == null || !performedOn.isBefore(today)) return OffsetDateTime.now();
        return performedOn.atTime(12, 0).atZone(ZONE).toOffsetDateTime();
    }

    /** Últimos {@code limit} puntos en orden cronológico. */
    public List<Point> series(UUID userWorldId, String metricKey, int limit) {
        List<Point> pts = new ArrayList<>(jdbc.query("""
                SELECT recorded_at, value FROM metric_entry WHERE user_world_id = ? AND metric_key = ?
                ORDER BY recorded_at DESC LIMIT ?
                """, (rs, i) -> new Point(rs.getObject(1, OffsetDateTime.class).atZoneSameInstant(ZONE).toLocalDate(),
                rs.getDouble(2)), userWorldId, metricKey, limit));
        java.util.Collections.reverse(pts);
        return pts;
    }

    /** Serie filtrando por un valor del contexto, p. ej. el 1RM estimado de un levantamiento concreto. */
    public List<Point> seriesWhere(UUID userWorldId, String metricKey, String contextKey, String contextValue,
            int limit) {
        List<Point> pts = new ArrayList<>(jdbc.query("""
                SELECT recorded_at, value FROM metric_entry
                WHERE user_world_id = ? AND metric_key = ? AND context ->> ? = ?
                ORDER BY recorded_at DESC LIMIT ?
                """, (rs, i) -> new Point(rs.getObject(1, OffsetDateTime.class).atZoneSameInstant(ZONE).toLocalDate(),
                rs.getDouble(2)), userWorldId, metricKey, contextKey, contextValue, limit));
        java.util.Collections.reverse(pts);
        return pts;
    }

    public Optional<Double> latest(UUID userWorldId, String metricKey) {
        return jdbc.query("""
                SELECT value FROM metric_entry WHERE user_world_id = ? AND metric_key = ?
                ORDER BY recorded_at DESC LIMIT 1
                """, (rs, i) -> rs.getDouble(1), userWorldId, metricKey).stream().findFirst();
    }

    public Optional<OffsetDateTime> latestAt(UUID userWorldId, String metricKey) {
        return jdbc.query("""
                SELECT recorded_at FROM metric_entry WHERE user_world_id = ? AND metric_key = ?
                ORDER BY recorded_at DESC LIMIT 1
                """, (rs, i) -> rs.getObject(1, OffsetDateTime.class), userWorldId, metricKey).stream().findFirst();
    }

    /** Mejor valor histórico (máximo si direction=up, mínimo si down). */
    public Optional<Double> best(UUID userWorldId, String metricKey, boolean higherIsBetter) {
        String agg = higherIsBetter ? "max" : "min";
        Double v = jdbc.queryForObject("SELECT " + agg + "(value) FROM metric_entry WHERE user_world_id = ? AND metric_key = ?",
                Double.class, userWorldId, metricKey);
        return Optional.ofNullable(v);
    }

    /** Últimos valores por cada valor distinto de una clave de contexto (p. ej. último 1RM por levantamiento). */
    public Map<String, Double> latestByContext(UUID userWorldId, String metricKey, String contextKey) {
        String k = safeKey(contextKey); // DISTINCT ON exige la misma expresión literal que ORDER BY
        Map<String, Double> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON (context ->> '%1$s') context ->> '%1$s', value FROM metric_entry
                WHERE user_world_id = ? AND metric_key = ? AND (context ->> '%1$s') IS NOT NULL
                ORDER BY context ->> '%1$s', recorded_at DESC
                """.formatted(k), rs -> {
            out.put(rs.getString(1), rs.getDouble(2));
        }, userWorldId, metricKey);
        return out;
    }

    /**
     * Agregado semanal (semanas de lunes a domingo) de las últimas {@code weeks} semanas, incluida la
     * actual. agg: sum | max | avg | count.
     */
    public List<Bar> weekly(UUID userWorldId, String metricKey, int weeks, String agg) {
        String fn = switch (agg) {
            case "max" -> "max(value)";
            case "avg" -> "avg(value)";
            case "count" -> "count(*)";
            default -> "sum(value)";
        };
        LocalDate thisMonday = LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate from = thisMonday.minusWeeks(weeks - 1L);
        Map<LocalDate, Double> byWeek = new LinkedHashMap<>();
        jdbc.query("""
                SELECT date_trunc('week', recorded_at AT TIME ZONE 'America/Bogota')::date AS wk, %s
                FROM metric_entry WHERE user_world_id = ? AND metric_key = ?
                  AND recorded_at >= (?::date)::timestamp AT TIME ZONE 'America/Bogota'
                GROUP BY wk
                """.formatted(fn), rs -> {
            byWeek.put(rs.getObject(1, LocalDate.class), rs.getDouble(2));
        }, userWorldId, metricKey, from);
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            LocalDate wk = from.plusWeeks(i);
            double v = byWeek.getOrDefault(wk, 0.0);
            String label = i == weeks - 1 ? "Esta" : wk.getDayOfMonth() + " " + wk.getMonth()
                    .getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("es")).replace(".", "");
            bars.add(new Bar(label, Math.round(v * 10) / 10.0, wk.toString()));
        }
        return bars;
    }

    /** Suma por valor de una clave de contexto en los últimos {@code days} días (p. ej. series por músculo). */
    public Map<String, Double> sumByContextSince(UUID userWorldId, String metricKey, String contextKey, int days) {
        Map<String, Double> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT context ->> ?, sum(value) FROM metric_entry
                WHERE user_world_id = ? AND metric_key = ? AND recorded_at >= now() - make_interval(days => ?)
                  AND (context ->> ?) IS NOT NULL
                GROUP BY 1
                """, rs -> {
            out.put(rs.getString(1), rs.getDouble(2));
        }, contextKey, userWorldId, metricKey, days, contextKey);
        return out;
    }

    /** Suma de los últimos {@code days} días (incluido hoy). */
    public double sumLastDays(UUID userWorldId, String metricKey, int days) {
        Double v = jdbc.queryForObject("""
                SELECT COALESCE(sum(value), 0) FROM metric_entry
                WHERE user_world_id = ? AND metric_key = ? AND recorded_at >= now() - make_interval(days => ?)
                """, Double.class, userWorldId, metricKey, days);
        return v == null ? 0 : v;
    }

    private static String safeKey(String key) {
        if (key == null || !key.matches("[a-zA-Z_][a-zA-Z0-9_]{0,40}")) {
            throw new IllegalArgumentException("Clave de contexto no válida: " + key);
        }
        return key;
    }

    public int count(UUID userWorldId, String metricKey) {
        Integer v = jdbc.queryForObject("SELECT count(*) FROM metric_entry WHERE user_world_id = ? AND metric_key = ?",
                Integer.class, userWorldId, metricKey);
        return v == null ? 0 : v;
    }

    /**
     * Últimos {@code limit} puntos de una métrica de nivel usuario (sin mundo: check-in diario, peso del Perfil 360),
     * en orden cronológico.
     */
    public List<Point> seriesForUser(UUID userId, String metricKey, int limit) {
        List<Point> pts = new ArrayList<>(jdbc.query("""
                SELECT recorded_at, value FROM metric_entry
                WHERE user_id = ? AND user_world_id IS NULL AND metric_key = ?
                ORDER BY recorded_at DESC LIMIT ?
                """, (rs, i) -> new Point(rs.getObject(1, OffsetDateTime.class).atZoneSameInstant(ZONE).toLocalDate(),
                rs.getDouble(2)), userId, metricKey, limit));
        java.util.Collections.reverse(pts);
        return pts;
    }
}
