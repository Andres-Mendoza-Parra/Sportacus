package com.sportacus.backend.world.goals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.world.goals.GoalProgress.Result;
import com.sportacus.backend.world.goals.GoalProgress.Sample;
import com.sportacus.backend.world.goals.GoalProgress.SessionFact;

class GoalProgressTest {

    /** Lunes. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
    private static final OffsetDateTime CREATED = at(TODAY.minusWeeks(10));

    private static OffsetDateTime at(LocalDate d) {
        return d.atTime(12, 0).atOffset(ZoneOffset.ofHours(-5));
    }

    private static final class Fake implements GoalProgress.Data {
        final Map<String, Integer> steps = new HashMap<>();
        final Map<String, List<Sample>> metrics = new HashMap<>();
        final Map<String, String> directions = new HashMap<>();
        final List<SessionFact> sessions = new ArrayList<>();
        int sum;

        Fake sample(String metric, double v, LocalDate day, String movement) {
            metrics.computeIfAbsent(metric, k -> new ArrayList<>()).add(new Sample(v, at(day), movement));
            return this;
        }

        Fake session(LocalDate day, Integer min, Double dist, Integer blockS, String... moves) {
            sessions.add(new SessionFact(day, min, dist, blockS, null, Set.of(moves)));
            return this;
        }

        @Override
        public int bestStep(String k) {
            return steps.getOrDefault(k, 0);
        }

        @Override
        public int sumSteps() {
            return sum;
        }

        @Override
        public List<Sample> metric(String k) {
            return metrics.getOrDefault(k, List.of());
        }

        @Override
        public String direction(String k) {
            return directions.getOrDefault(k, "up");
        }

        @Override
        public List<SessionFact> sessions() {
            return sessions;
        }
    }

    private static Result eval(Map<String, Object> target, Double baseline, Fake d) {
        return GoalProgress.evaluate(target, CREATED, baseline, d, TODAY);
    }

    @Test
    void escalonDeProgresion() {
        Fake d = new Fake();
        d.steps.put("cal_jale", 2);
        Result r = eval(Map.of("progression", "cal_jale", "step", 4), null, d);
        assertTrue(r.measurable());
        assertFalse(r.reached());
        assertEquals(2.0, r.baseline()); // se propone guardar el punto de partida
        assertEquals(0, r.pct());
        d.steps.put("cal_jale", 3);
        assertEquals(50, eval(Map.of("progression", "cal_jale", "step", 4), 2.0, d).pct());
        d.steps.put("cal_jale", 4);
        Result done = eval(Map.of("progression", "cal_jale", "step", 4), 2.0, d);
        assertTrue(done.reached());
        assertEquals(100, done.pct());
    }

    @Test
    void umbralMinimoConMovimiento() {
        Fake d = new Fake().sample("cal_max_reps", 6, TODAY.minusDays(3), "fondos_paralelas")
                .sample("cal_max_reps", 20, TODAY.minusDays(2), "flexiones");
        Map<String, Object> t = Map.of("metric", "cal_max_reps", "movement", "fondos_paralelas", "reps", 10);
        Result r = eval(t, null, d);
        assertFalse(r.reached());
        assertEquals(60, r.pct());
        d.sample("cal_max_reps", 10, TODAY, "fondos_paralelas");
        assertTrue(eval(t, null, d).reached());
    }

    @Test
    void umbralMaximoUsaElPuntoDePartida() {
        Fake d = new Fake().sample("hea_tug_s", 16, TODAY.minusWeeks(9), null).sample("hea_tug_s", 14, TODAY, null);
        Result r = eval(Map.of("metric", "hea_tug_s", "max", 12), null, d);
        assertFalse(r.reached());
        assertEquals(50, r.pct());
        assertEquals(16.0, r.baseline());
        d.sample("hea_tug_s", 11.5, TODAY, null);
        assertTrue(eval(Map.of("metric", "hea_tug_s", "max", 12), 16.0, d).reached());
    }

    @Test
    void valorSigueLaDireccionDeLaMetrica() {
        Fake d = new Fake().sample("run_5k_tiempo_s", 1700, TODAY.minusDays(1), null);
        d.directions.put("run_5k_tiempo_s", "down");
        assertTrue(eval(Map.of("metric", "run_5k_tiempo_s", "value", 1728), null, d).reached());
        assertFalse(eval(Map.of("metric", "run_5k_tiempo_s", "value", 1600), null, d).reached());
    }

    @Test
    void mejorarUnPorcentaje() {
        Fake d = new Fake().sample("gym_e1rm", 100, TODAY.minusWeeks(11), "sentadilla_barra");
        Map<String, Object> t = Map.of("metric", "gym_e1rm", "movement", "sentadilla_barra", "improvePct", 10);
        Result r = eval(t, null, d);
        assertEquals(100.0, r.baseline());
        assertEquals(0, r.pct());
        d.sample("gym_e1rm", 105, TODAY.minusWeeks(2), "sentadilla_barra");
        assertEquals(50, eval(t, 100.0, d).pct());
        d.sample("gym_e1rm", 110, TODAY, "sentadilla_barra");
        assertTrue(eval(t, 100.0, d).reached());

        // Menos es mejor (sprint): 3 % menos.
        Fake s = new Fake().sample("fut_sprint_30m_s", 5.0, TODAY.minusWeeks(9), null)
                .sample("fut_sprint_30m_s", 4.85, TODAY, null);
        s.directions.put("fut_sprint_30m_s", "down");
        assertTrue(eval(Map.of("metric", "fut_sprint_30m_s", "improvePct", 3), null, s).reached());
    }

    @Test
    void mejorarSinDatosPideElPuntoDePartida() {
        Result r = eval(Map.of("metric", "gym_e1rm", "improvePct", 10), null, new Fake());
        assertTrue(r.measurable());
        assertEquals(0, r.pct());
        assertNull(r.baseline());
    }

    @Test
    void gananciaDeEscalones() {
        Fake d = new Fake();
        d.sum = 5;
        Result r = eval(Map.of("metric", "cal_escalones", "gain", 4), null, d);
        assertEquals(5.0, r.baseline());
        d.sample("cal_escalones", 7, TODAY, null);
        assertEquals(50, eval(Map.of("metric", "cal_escalones", "gain", 4), 5.0, d).pct());
        d.sample("cal_escalones", 9, TODAY, null);
        assertTrue(eval(Map.of("metric", "cal_escalones", "gain", 4), 5.0, d).reached());
    }

    @Test
    void sumaSemanal() {
        Fake d = new Fake().sample("minutos_sesion", 60, TODAY, null).sample("minutos_sesion", 30, TODAY, null);
        Result r = eval(Map.of("metric", "minutos_sesion", "weeklySum", 150), null, d);
        assertEquals(60, r.pct());
        assertFalse(r.reached());
        d.sample("minutos_sesion", 60, TODAY.minusWeeks(1), null).sample("minutos_sesion", 90, TODAY.minusWeeks(1), null);
        assertTrue(eval(Map.of("metric", "minutos_sesion", "weeklySum", 150), null, d).reached());

        Fake s = new Fake().session(TODAY, 80, null, null).session(TODAY, 80, null, null);
        assertTrue(eval(Map.of("minutesPerWeek", 150), null, s).reached());
    }

    @Test
    void semanasSeguidasConSesiones() {
        Fake d = new Fake();
        for (int w = 1; w <= 3; w++) {
            LocalDate monday = TODAY.minusWeeks(w);
            d.session(monday, 40, null, null).session(monday.plusDays(2), 40, null, null).session(monday.plusDays(4), 40, null, null);
        }
        Result r = eval(Map.of("sessionsPerWeek", 3, "weeks", 8), null, d);
        assertEquals("3 de 8 semanas seguidas", r.text()); // la semana en curso (sin terminar) no rompe la racha
        assertFalse(r.reached());
        assertTrue(eval(Map.of("sessionsPerWeek", 3, "weeks", 3), null, d).reached());
    }

    @Test
    void conteoDeSesionesYMovimientos() {
        Fake d = new Fake().session(TODAY.minusDays(1), 30, null, null, "fut_nordico")
                .session(TODAY.minusWeeks(2), 30, null, null, "fut_copenhague")
                .session(TODAY.minusWeeks(20), 30, null, null); // antes de crear la meta: no cuenta
        assertEquals("2 de 12 sesiones", eval(Map.of("sessions", 12), null, d).text());
        Result prev = eval(Map.of("weeks", 4, "movements", List.of("fut_nordico", "fut_copenhague")), null, d);
        assertEquals(50, prev.pct());
    }

    @Test
    void distanciaYMinutosSeguidos() {
        Fake d = new Fake().session(TODAY, 35, 3500.0, 20 * 60);
        Result r = eval(Map.of("minutes", 30, "km", 5), null, d);
        assertEquals(70, r.pct());
        assertFalse(r.reached());
        d.session(TODAY, 35, null, 30 * 60);
        assertTrue(eval(Map.of("minutes", 30, "km", 5), null, d).reached());
        assertFalse(eval(Map.of("km", 10), null, d).reached());
    }

    @Test
    void rangoSostenidoVariasSemanas() {
        Fake d = new Fake();
        for (int w = 0; w < 4; w++) d.sample("fut_acwr", 1.0, TODAY.minusWeeks(w), null);
        assertTrue(eval(Map.of("metric", "fut_acwr", "min", 0.8, "max", 1.3, "weeks", 4), null, d).reached());
        Fake bad = new Fake().sample("fut_acwr", 1.6, TODAY, null);
        assertFalse(eval(Map.of("metric", "fut_acwr", "min", 0.8, "max", 1.3, "weeks", 4), null, bad).reached());
    }

    @Test
    void objetivosNoMediblesQuedanManuales() {
        Result r = eval(Map.of("weeklySets", 10, "weeks", 8), null, new Fake());
        assertFalse(r.measurable());
        assertNull(r.pct());
        assertFalse(eval(Map.of(), null, new Fake()).measurable());
    }

    @Test
    void formatoEnEspanol() {
        assertEquals("12", GoalProgress.fmt(12.0));
        assertEquals("4,6", GoalProgress.fmt(4.56));
    }
}
