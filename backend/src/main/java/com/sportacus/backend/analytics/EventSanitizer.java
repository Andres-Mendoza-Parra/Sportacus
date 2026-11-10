package com.sportacus.backend.analytics;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Limpia los eventos que manda la app (reglas puras). La analítica es propia y mínima: solo se guardan propiedades
 * cortas tipo categoría (claves, pantallas, mundos), números y booleanos. Nada de texto libre ni datos de salud: los
 * valores de texto que no parecen una categoría se descartan.
 */
public final class EventSanitizer {

    public static final int MAX_EVENTS = 50;
    public static final int MAX_PROPS = 10;
    private static final Pattern KEY = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{0,39}");
    private static final Pattern CATEGORY = Pattern.compile("[A-Za-z0-9_\\-/.:\\[\\]]{1,60}");
    private static final Duration MAX_AGE = Duration.ofDays(7);
    private static final Duration MAX_SKEW = Duration.ofMinutes(5);

    private EventSanitizer() {
    }

    public static boolean validName(String name) {
        return name != null && AnalyticsService.NAME.matcher(name).matches();
    }

    public static Map<String, Object> props(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw == null) return out;
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (out.size() >= MAX_PROPS) break;
            if (e.getKey() == null || !KEY.matcher(e.getKey()).matches()) continue;
            Object v = e.getValue();
            if (v instanceof Boolean) {
                out.put(e.getKey(), v);
            } else if (v instanceof Number n && Double.isFinite(n.doubleValue())) {
                out.put(e.getKey(), n);
            } else if (v instanceof String s && CATEGORY.matcher(s).matches()) {
                out.put(e.getKey(), s);
            }
        }
        return out;
    }

    /** Fecha del evento: la que manda la app si es razonable (≤ 7 días atrás, sin futuro); si no, ahora. */
    public static OffsetDateTime at(String raw, OffsetDateTime now) {
        if (raw == null || raw.isBlank()) return now;
        try {
            OffsetDateTime t = OffsetDateTime.parse(raw);
            if (t.isAfter(now.plus(MAX_SKEW)) || t.isBefore(now.minus(MAX_AGE))) return now;
            return t;
        } catch (RuntimeException e) {
            return now;
        }
    }
}
