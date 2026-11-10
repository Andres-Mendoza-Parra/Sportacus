package com.sportacus.backend.world.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.world.core.Prescription;

class DoseTextTest {

    @Test
    void seriesRepeticionesRirTempoYDescanso() {
        Prescription p = Prescription.reps(4, 8, 12, 90).withRir(2).withTempo("2-1-2");
        List<String> out = DoseText.explain(p, null);
        assertEquals("4 series de entre 8 y 12 repeticiones.", out.get(0));
        assertTrue(out.stream().anyMatch(s -> s.startsWith("RIR 2: termina cada serie sintiendo que podrías hacer 2 repeticiones más")));
        assertTrue(out.stream().anyMatch(s -> s.startsWith("Ritmo 2-1-2: baja en 2 s, pausa 1 s y sube en 2 s")));
        assertTrue(out.contains("Descansa 1 min 30 s entre series."));
        assertEquals("4 × 8–12", DoseText.shortText(p));
    }

    @Test
    void sostenimientoPorLadoYLimiteDeDolor() {
        Prescription p = Prescription.hold(3, 30, 45).withSide("cada_lado").withPainMax(3);
        List<String> out = DoseText.explain(p, null);
        assertEquals("3 series: sostén la posición 30 s por cada lado.", out.get(0));
        assertTrue(out.stream().anyMatch(s -> s.startsWith("Límite de dolor: 3/10")));
    }

    @Test
    void checkinDeHoyReduceSeries() {
        List<String> out = DoseText.explain(Prescription.reps(4, 10, 60), 0.75);
        assertTrue(out.get(out.size() - 1).startsWith("Hoy, según tu check-in: haz 3 series en vez de 4 (−25 %)"));
    }

    @Test
    void tiempoIntervalosYDistancia() {
        assertEquals("Durante 20 min.", DoseText.explain(Prescription.time(1200, "suave"), null).get(0));
        assertEquals("6 repeticiones: 30 s de trabajo y 1 min 30 s de recuperación.",
                DoseText.explain(Prescription.interval(6, 30, 90, "fuerte"), null).get(0));
        assertEquals("Recorre 5 km.", DoseText.explain(Prescription.distance(5000, null, "suave"), null).get(0));
        assertNull(DoseText.shortText(null));
    }

    @Test
    void criteriosDeDesbloqueoEnTexto() {
        assertEquals("Para avanzar: 3 series de 8 repeticiones con buena técnica.",
                BlockExplainer.unlockText(Map.of("type", "reps", "sets", 3, "reps", 8)));
        assertEquals("Para avanzar: 3 series de 20 s.", BlockExplainer.unlockText(Map.of("type", "hold", "sets", 3, "seconds", 20)));
        assertTrue(BlockExplainer.unlockText(Map.of("type", "pain", "sessions", 6, "maxDuring", 3)).contains("6 sesiones"));
        assertNull(BlockExplainer.unlockText(Map.of()));
    }
}
