package com.sportacus.backend.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EventSanitizerTest {

    @Test
    void nombres() {
        assertTrue(EventSanitizer.validName("screen_view"));
        assertFalse(EventSanitizer.validName("ab"));
        assertFalse(EventSanitizer.validName("Screen_view"));
        assertFalse(EventSanitizer.validName("screen view"));
        assertFalse(EventSanitizer.validName(null));
    }

    @Test
    void soloCategoriasNumerosYBooleanos() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("screen", "/home");
        raw.put("world", "gym");
        raw.put("minutes", 45);
        raw.put("ok", true);
        raw.put("texto", "me duele la rodilla"); // texto libre: fuera
        raw.put("obj", Map.of("a", 1));
        raw.put("mala clave", "x");
        Map<String, Object> out = EventSanitizer.props(raw);
        assertEquals(Map.of("screen", "/home", "world", "gym", "minutes", 45, "ok", true), out);
    }

    @Test
    void fechas() {
        OffsetDateTime now = OffsetDateTime.of(2026, 11, 9, 12, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(now, EventSanitizer.at(null, now));
        assertEquals(now, EventSanitizer.at("no-es-fecha", now));
        assertEquals(now, EventSanitizer.at("2026-11-10T12:00:00Z", now)); // futuro
        assertEquals(now, EventSanitizer.at("2026-10-01T12:00:00Z", now)); // > 7 días
        assertEquals(now.minusHours(2), EventSanitizer.at("2026-11-09T10:00:00Z", now));
    }
}
