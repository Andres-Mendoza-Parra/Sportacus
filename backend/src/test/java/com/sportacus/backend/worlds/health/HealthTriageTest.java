package com.sportacus.backend.worlds.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class HealthTriageTest {

    @Test
    void eligeRutaPorZonaEdadYCondicion() {
        assertEquals("rodilla", HealthTriage.chooseTrack("rodilla", List.of(), 70, true, Set.of(), "cronica"));
        assertEquals("tobillo", HealthTriage.chooseTrack(null, List.of("pie"), 30, false, Set.of(), "lesion_reciente"));
        assertEquals("adulto_mayor", HealthTriage.chooseTrack("ninguna", List.of(), 71, false, Set.of(), "prevencion_edad"));
        assertEquals("adulto_mayor", HealthTriage.chooseTrack("ninguna", List.of(), 50, true, Set.of(), "prevencion_edad"));
        assertEquals("cardiometabolico", HealthTriage.chooseTrack("ninguna", List.of(), 50, false, Set.of("hipertension"), "cronica"));
        assertEquals("movilidad_general", HealthTriage.chooseTrack("ninguna", List.of(), 30, false, Set.of(), "postura"));
    }

    @Test
    void faseInicialConservadora() {
        // Dolor alto, agudo o postoperatorio → fase 1.
        assertEquals(0, HealthTriage.initialPhase("rodilla", "cronica", "mas_3m", 2, 7, false, false, false, false, 40, null));
        assertEquals(0, HealthTriage.initialPhase("rodilla", "dolor_sin_lesion", "menos_6s", 1, 2, false, false, false, false, 40, null));
        assertEquals(0, HealthTriage.initialPhase("rodilla", "postoperatorio", "6s_3m", 0, 1, false, false, false, false, 40, null));
        assertEquals(0, HealthTriage.initialPhase("lumbar", "cronica", "mas_3m", 1, 2, false, true, false, false, 40, null));
        // Dolor bajo y crónico → fase 2.
        assertEquals(1, HealthTriage.initialPhase("lumbar", "cronica", "mas_3m", 1, 3, false, false, false, false, 40, null));
        // Adulto mayor con caídas → fase 1; sin caídas y activo → fase 2.
        assertEquals(0, HealthTriage.initialPhase("adulto_mayor", "prevencion_edad", null, 0, 0, false, false, false, true, 70, null));
        assertEquals(1, HealthTriage.initialPhase("adulto_mayor", "prevencion_edad", null, 0, 0, false, false, false, false, 70, null));
        assertEquals(0, HealthTriage.initialPhase("cardiometabolico", "cronica", "mas_3m", 0, 0, false, false, true, false, 50, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void normasSteadiPorEdadYSexo() throws Exception {
        String json = new String(HealthTriageTest.class.getResourceAsStream("/content/worlds/health.json").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        Map<String, Object> content = tools.jackson.databind.json.JsonMapper.builder().build().readValue(json, Map.class);
        List<Map<String, Object>> tests = (List<Map<String, Object>>) content.get("assessments");
        Map<String, Object> chair = (Map<String, Object>) tests.stream().filter(t -> "hea_test_silla_30s".equals(t.get("key")))
                .findFirst().orElseThrow().get("norms");
        // Hombre 72 años: < 12 = bajo el promedio (STEADI).
        assertTrue(HealthTriage.isRisk(HealthTriage.matchBand(chair, 11, 72, "masculino")));
        assertFalse(HealthTriage.isRisk(HealthTriage.matchBand(chair, 12, 72, "masculino")));
        // Mujer 62 años: < 12; mujer 91 años: < 4.
        assertTrue(HealthTriage.isRisk(HealthTriage.matchBand(chair, 11, 62, "femenino")));
        assertFalse(HealthTriage.isRisk(HealthTriage.matchBand(chair, 4, 91, "femenino")));
        // Menor de 60: sin norma STEADI (banda de seguimiento, sin riesgo).
        assertFalse(HealthTriage.isRisk(HealthTriage.matchBand(chair, 5, 40, "masculino")));

        Map<String, Object> tug = (Map<String, Object>) tests.stream().filter(t -> "hea_test_tug".equals(t.get("key")))
                .findFirst().orElseThrow().get("norms");
        assertTrue(HealthTriage.isRisk(HealthTriage.matchBand(tug, 12, 70, "femenino")));
        assertFalse(HealthTriage.isRisk(HealthTriage.matchBand(tug, 11.9, 70, "femenino")));

        Map<String, Object> balance = (Map<String, Object>) tests.stream().filter(t -> "hea_test_equilibrio_4".equals(t.get("key")))
                .findFirst().orElseThrow().get("norms");
        assertTrue(HealthTriage.isRisk(HealthTriage.matchBand(balance, 2, 70, "femenino")));
        assertFalse(HealthTriage.isRisk(HealthTriage.matchBand(balance, 3, 70, "femenino")));
    }

    @Test
    void tempoYDuracionEstimada() {
        assertEquals(7, HealthWorld.tempoSeconds("3-1-3"));
        assertEquals(7, HealthWorld.tempoSeconds("sostén 5 s"));
        assertEquals(4, HealthWorld.tempoSeconds(null));
    }
}
