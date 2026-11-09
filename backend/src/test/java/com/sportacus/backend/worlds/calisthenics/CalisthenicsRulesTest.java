package com.sportacus.backend.worlds.calisthenics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.worlds.calisthenics.CalisthenicsRules.Flags;
import com.sportacus.backend.worlds.calisthenics.CalisthenicsRules.Maxes;
import com.sportacus.backend.worlds.calisthenics.CalisthenicsRules.SetLog;

class CalisthenicsRulesTest {

    private static Maxes maxes(int push, int pull, int dips, int plank, int squats, String hs) {
        return new Maxes(push, pull, dips, plank, squats, hs);
    }

    @Test
    void ubicaJaleSegunDominadas() {
        // 0 dominadas y poca fuerza: colgado de barra.
        assertEquals(0, CalisthenicsRules.place("jale", maxes(2, 0, 0, 20, 10, "nunca"), Flags.none()).step());
        // 0 dominadas pero buena fuerza general: remo australiano.
        assertEquals(2, CalisthenicsRules.place("jale", maxes(20, 0, 0, 60, 30, "nunca"), Flags.none()).step());
        // 1–2 dominadas: negativas; 3–9: dominada; 12: dominada al pecho.
        assertEquals(3, CalisthenicsRules.place("jale", maxes(10, 2, 0, 60, 30, "nunca"), Flags.none()).step());
        assertEquals(4, CalisthenicsRules.place("jale", maxes(10, 5, 0, 60, 30, "nunca"), Flags.none()).step());
        assertEquals(5, CalisthenicsRules.place("jale", maxes(30, 12, 10, 60, 30, "nunca"), Flags.none()).step());
    }

    @Test
    void ubicaEmpujeConCuidadoSiHayFragilidad() {
        assertEquals(1, CalisthenicsRules.pushPlacement(0, Flags.none()).step());
        assertEquals(0, CalisthenicsRules.pushPlacement(0, new Flags(true, false, false, false)).step());
        assertEquals(3, CalisthenicsRules.pushPlacement(8, Flags.none()).step());
        assertEquals(7, CalisthenicsRules.pushPlacement(50, Flags.none()).step());
    }

    @Test
    void topePiernasConLesionDeRodilla() {
        assertEquals(4, CalisthenicsRules.legPlacement(70, Flags.none()).step());
        assertEquals(2, CalisthenicsRules.legPlacement(70, new Flags(false, true, false, false)).step());
    }

    @Test
    void lsitSoloSiLoBuscaYTieneApoyo() {
        assertEquals(1, CalisthenicsRules.corePlacement(60, 12, Flags.none()).step());
        assertEquals(4, CalisthenicsRules.corePlacement(60, 12, new Flags(false, false, false, true)).step());
    }

    @Test
    void criterioDeDesbloqueo() {
        Map<String, Object> reps = Map.of("type", "reps", "sets", 3, "reps", 8);
        assertTrue(CalisthenicsRules.criteriaMet(reps, List.of(new SetLog(8, null), new SetLog(9, null), new SetLog(8, null))));
        assertFalse(CalisthenicsRules.criteriaMet(reps, List.of(new SetLog(8, null), new SetLog(7, null), new SetLog(8, null))));
        assertEquals(1, CalisthenicsRules.gap(reps, List.of(new SetLog(8, null), new SetLog(7, null), new SetLog(8, null))));
        Map<String, Object> hold = Map.of("type", "hold", "sets", 3, "seconds", 30);
        assertTrue(CalisthenicsRules.criteriaMet(hold, List.of(new SetLog(null, 30), new SetLog(null, 31), new SetLog(null, 35))));
        assertEquals(30, CalisthenicsRules.gap(hold, List.of(new SetLog(null, 30), new SetLog(null, 30))));
    }

    @Test
    void sesionMuyPorDebajo() {
        assertTrue(CalisthenicsRules.farBelow(8, List.of(new SetLog(3, null), new SetLog(4, null)), false));
        assertFalse(CalisthenicsRules.farBelow(8, List.of(new SetLog(6, null), new SetLog(5, null)), false));
    }

    @Test
    void repartoEntrenaCadaArbolDosVeces() {
        List<String> trees = List.of("empuje", "jale", "fondos", "piernas", "core");
        for (int days = 2; days <= 5; days++) {
            List<List<String>> s = CalisthenicsRules.schedule(trees, days, 6);
            Map<String, Integer> count = new HashMap<>();
            s.forEach(day -> day.forEach(t -> count.merge(t, 1, Integer::sum)));
            for (String t : trees) assertTrue(count.getOrDefault(t, 0) >= 2, "días=" + days + " árbol=" + t);
            s.forEach(day -> assertEquals(day.size(), day.stream().distinct().count()));
        }
        // La parada de manos va primero en la sesión (habilidad técnica con el sistema fresco).
        assertEquals("parada_manos", CalisthenicsRules.schedule(List.of("core", "jale", "parada_manos"), 3, 6).get(0).get(0));
    }

    @Test
    void nivelSegunProgresoMedio() {
        assertEquals(1, CalisthenicsRules.level(Map.of("a", 0, "b", 0), Map.of("a", 7, "b", 7)));
        assertEquals(5, CalisthenicsRules.level(Map.of("a", 7, "b", 7), Map.of("a", 7, "b", 7)));
        assertEquals(3, CalisthenicsRules.level(Map.of("a", 4, "b", 3), Map.of("a", 7, "b", 7)));
    }
}
