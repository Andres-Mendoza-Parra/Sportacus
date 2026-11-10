package com.sportacus.backend.worlds.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.core.Factor;
import com.sportacus.backend.world.core.ProfileFacts;
import com.sportacus.backend.world.core.ProfileInsights;

class HealthAdaptationsTest {

    private static HealthSummary screened(String... flags) {
        return new HealthSummary(true, "bajo", List.of(flags), false, false, false, Set.of(), List.of(), null);
    }

    private static ProfileInsights insights(boolean hrUnreliable, boolean hypo, boolean physicalJob, Integer sitting) {
        return new ProfileInsights(ProfileFacts.empty(), null, null, hrUnreliable, hypo, false, false, false, false,
                false, false, false, false, false, false, physicalJob, sitting, null, null, null, List.of(), null, null,
                null, null, null);
    }

    private static Answers answers(Map<String, Object> m) {
        return new Answers(m);
    }

    @Test
    void variantesSentadasEnSillaDeRuedas() {
        assertTrue(HealthAdaptations.isSeated("hea_marcha_sentado"));
        assertTrue(HealthAdaptations.isSeated("hea_cardio_silla"));
        assertFalse(HealthAdaptations.isSeated("hea_test_silla_30s"));
        assertFalse(HealthAdaptations.isSeated("hea_minisentadilla_apoyo"));
        List<String> pool = List.of("hea_puente", "hea_marcha_sentado", "hea_extension_rodilla_sentado");
        assertEquals(List.of("hea_marcha_sentado", "hea_extension_rodilla_sentado"),
                HealthAdaptations.seatedOnlyIfWheelchair(pool, Function.identity(), true));
        assertEquals(pool, HealthAdaptations.seatedOnlyIfWheelchair(pool, Function.identity(), false));
        // Sin variantes sentadas en la ruta: no se deja el plan vacío.
        assertEquals(List.of("hea_puente"), HealthAdaptations.seatedOnlyIfWheelchair(List.of("hea_puente"), Function.identity(), true));
    }

    @Test
    void movilidadReducidaDesdeElChequeo() {
        assertTrue(HealthAdaptations.reducedMobility(screened("movilidad_reducida")));
        assertTrue(HealthAdaptations.wheelchair(screened("movilidad_reducida", "silla_ruedas")));
        assertFalse(HealthAdaptations.reducedMobility(screened()));
        List<String> notices = HealthAdaptations.enrollNotices(answers(Map.of("h_reason", "postura")),
                screened("movilidad_reducida", "silla_ruedas"), 40);
        assertTrue(notices.stream().anyMatch(n -> n.contains("silla de ruedas")));
    }

    @Test
    void riesgoDeCaidasPrudenteSinRespuesta() {
        Answers none = answers(Map.of("h_reason", "prevencion_edad"));
        assertTrue(HealthAdaptations.initialFallRisk(none, screened(), 62));
        assertFalse(HealthAdaptations.initialFallRisk(none, screened(), 40));
        assertFalse(HealthAdaptations.declaredFallRisk(none, screened()));
        assertFalse(HealthAdaptations.initialFallRisk(answers(Map.of("h_falls", "no")), screened(), 70));
        assertTrue(HealthAdaptations.declaredFallRisk(none, screened("baja_vision")));
        assertTrue(HealthAdaptations.enrollNotices(none, screened(), 62).stream().anyMatch(n -> n.contains("STEADI")));
    }

    @Test
    void sinFechaDeInicioEmpiezaEnFase1() {
        List<String> notices = HealthAdaptations.enrollNotices(answers(Map.of("h_reason", "cronica")), screened(), 40);
        assertTrue(notices.stream().anyMatch(n -> n.contains("hace cuánto")));
        assertEquals(0, HealthTriage.initialPhase("lumbar", "cronica", null, 1, 2, false, false, false, false, 40, null));
        // Sin molestia (postura), la pregunta no aplica: se mantiene la fase 2.
        assertEquals(1, HealthTriage.initialPhase("lumbar", "postura", null, 0, 0, false, false, false, false, 40, null));
        assertEquals(1, HealthTriage.initialPhase("lumbar", null, null, 0, 0, false, false, false, false, 40, null));
    }

    @Test
    void modoPrudenteUsaElMensajeDelNucleo() {
        HealthSummary unscreened = HealthSummary.unknown();
        List<String> notices = HealthAdaptations.enrollNotices(answers(Map.of("h_reason", "postura")), unscreened, 30);
        assertTrue(notices.stream().anyMatch(n -> n.startsWith("Modo prudente")));
        List<Factor> f = HealthAdaptations.planFactors("movilidad_general", unscreened, ProfileInsights.empty());
        assertTrue(f.stream().anyMatch(x -> x.text().equals(unscreened.moderateReason())));
        assertTrue(HealthAdaptations.planFactors("movilidad_general", screened(), ProfileInsights.empty()).isEmpty());
    }

    @Test
    void perfil360CitaElDato() {
        ProfileInsights in = insights(true, true, true, 10);
        List<Factor> f = HealthAdaptations.planFactors("movilidad_general", screened(), in);
        assertTrue(f.stream().anyMatch(x -> x.text().contains("betabloqueadores")));
        assertTrue(f.stream().anyMatch(x -> x.text().contains("insulina")));
        assertTrue(f.stream().anyMatch(x -> x.text().contains("físicamente pesado")));
        assertTrue(f.stream().anyMatch(x -> x.text().contains("10 h sentado")));
        String note = HealthAdaptations.sessionNote("cardiometabolico", in);
        assertTrue(note.contains("carbohidrato") && note.contains("pulso"));
        assertNull(HealthAdaptations.sessionNote("rodilla", insights(false, false, false, null)));
        assertEquals(4, HealthAdaptations.tips("cardiometabolico", screened(), in).size());
        assertTrue(HealthAdaptations.tips("rodilla", screened(), insights(false, false, false, 5)).isEmpty());
    }
}
