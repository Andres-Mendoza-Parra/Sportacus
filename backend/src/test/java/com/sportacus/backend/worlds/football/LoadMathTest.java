package com.sportacus.backend.worlds.football;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.worlds.football.LoadMath.DayLoad;

class LoadMathTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    @Test
    void sinTresSemanasNoHayAcwr() {
        List<DayLoad> l = List.of(new DayLoad(TODAY.minusDays(10), 400), new DayLoad(TODAY, 400));
        assertTrue(LoadMath.acwr(l, TODAY, TODAY.minusDays(10)).isEmpty());
    }

    @Test
    void cargaEstableDaUno() {
        List<DayLoad> l = new ArrayList<>();
        for (int d = 0; d < 28; d += 2) l.add(new DayLoad(TODAY.minusDays(d), 300));
        var a = LoadMath.acwr(l, TODAY, TODAY.minusDays(27)).orElseThrow();
        // 14 sesiones en 28 días: aguda = 4 × 300, crónica = 3,5 × 300 por semana.
        assertEquals(1200, a.acute(), 0.01);
        assertEquals(1050, a.chronicWeekly(), 0.5);
        assertEquals(1.14, a.ratio(), 0.01);
        assertEquals("optima", LoadMath.zone(a.ratio()));
    }

    @Test
    void picoDeCargaEsRiesgo() {
        List<DayLoad> l = new ArrayList<>();
        for (int w = 1; w < 4; w++) l.add(new DayLoad(TODAY.minusDays(7L * w + 1), 300));
        for (int d = 0; d < 5; d++) l.add(new DayLoad(TODAY.minusDays(d), 400));
        var a = LoadMath.acwr(l, TODAY, TODAY.minusDays(27)).orElseThrow();
        assertEquals("riesgo", LoadMath.zone(a.ratio()));
    }

    @Test
    void historialDe21DiasNoInflaElCociente() {
        List<DayLoad> l = new ArrayList<>();
        for (int d = 0; d < 21; d++) l.add(new DayLoad(TODAY.minusDays(d), 100));
        var a = LoadMath.acwr(l, TODAY, TODAY.minusDays(20)).orElseThrow();
        assertEquals(1.0, a.ratio(), 0.001);
    }

    @Test
    void zonas() {
        assertEquals("baja", LoadMath.zone(0.7));
        assertEquals("optima", LoadMath.zone(1.3));
        assertEquals("precaucion", LoadMath.zone(1.45));
        assertEquals("riesgo", LoadMath.zone(1.6));
    }

    @Test
    void totalesSemanales() {
        List<DayLoad> l = List.of(new DayLoad(TODAY, 100), new DayLoad(TODAY.minusDays(7), 50));
        var weeks = LoadMath.weeklyTotals(l, TODAY, 3);
        assertEquals(3, weeks.size());
        assertEquals(100, weeks.get(2).total(), 0.01); // 8-nov-2026 es domingo: semana actual
        assertEquals(50, weeks.get(1).total(), 0.01);
    }
}
