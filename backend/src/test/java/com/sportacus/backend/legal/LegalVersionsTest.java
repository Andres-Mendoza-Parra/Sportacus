package com.sportacus.backend.legal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.common.Json;

class LegalVersionsTest {

    private static final Map<String, String> CURRENT = Map.of("politica_datos", "1.0", "terminos", "1.0");

    @Test
    void versionVigente() {
        assertTrue(LegalVersions.upToDate("1.0", "1.0"));
        assertFalse(LegalVersions.upToDate("1.1", "1.0"));
        assertFalse(LegalVersions.upToDate("1.0", null)); // usuarios anteriores sin aceptación registrada
    }

    @Test
    void aceptacion() {
        assertTrue(LegalVersions.acceptanceProblem("politica_datos", "1.0", CURRENT).isEmpty());
        assertTrue(LegalVersions.acceptanceProblem("terminos", " 1.0 ", CURRENT).isEmpty());
        assertTrue(LegalVersions.acceptanceProblem("terminos", "0.9", CURRENT).isPresent());
        assertTrue(LegalVersions.acceptanceProblem("otro", "1.0", CURRENT).isPresent());
        assertTrue(LegalVersions.acceptanceProblem(null, "1.0", CURRENT).isPresent());
        assertTrue(LegalVersions.acceptanceProblem("terminos", null, CURRENT).isPresent());
    }

    @Test
    void documentosBienFormados() throws Exception {
        Json json = new Json();
        for (String key : LegalService.DOCUMENTS) {
            try (InputStream in = getClass().getResourceAsStream("/content/legal/" + key + ".json")) {
                LegalService.LegalDocument d = json.read(new String(in.readAllBytes(), StandardCharsets.UTF_8),
                        LegalService.LegalDocument.class);
                assertEquals(key, d.key());
                assertEquals("1.0", d.version());
                assertEquals("2026-11-09", d.updatedAt());
                assertFalse(d.sections().isEmpty());
                d.sections().forEach(s -> assertFalse(s.title().isBlank() || s.body().isBlank()));
            }
        }
    }
}
