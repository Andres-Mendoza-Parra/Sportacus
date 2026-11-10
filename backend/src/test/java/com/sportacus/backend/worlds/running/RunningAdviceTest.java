package com.sportacus.backend.worlds.running;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.profile.InsightRules;
import com.sportacus.backend.world.core.Factor;
import com.sportacus.backend.world.core.GeneratedPlan;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedSession;
import com.sportacus.backend.world.core.ProfileFacts;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.worlds.running.RunningMath.Paces;
import com.sportacus.backend.worlds.running.RunningPlanBuilder.Input;
import com.sportacus.backend.worlds.running.RunningPlanBuilder.Mode;

class RunningAdviceTest {

    static ProfileInsights insights(Map<String, Object> facts, List<String> healthFlags) {
        HealthSummary h = new HealthSummary(true, "bajo", healthFlags, false, false, false, new java.util.HashSet<>(), List.of(), null);
        return InsightRules.derive(new ProfileFacts(new Answers(new java.util.HashMap<>(facts)), new java.util.HashMap<>()), h, LocalDate.now());
    }

    @Test
    void altitudeSlowsTrainingPacesButKeepsReference() {
        Paces p = RunningMath.paces(1500); // 5:00 /km
        Paces alt = p.scaled(1.04);
        assertEquals(1500, alt.fiveKS());
        assertEquals(312, alt.race());
        assertEquals(Math.round(p.easy() * 1.04), alt.easy(), 1);
        assertSame(p, p.scaled(1.0));
    }

    @Test
    void watchPrefilledOnlyWhenItMaps() {
        assertEquals("gps", RunningAdvice.watchFromDevices(List.of("pulsometro", "reloj_gps")));
        assertEquals("movil", RunningAdvice.watchFromDevices(List.of("solo_celular")));
        assertNull(RunningAdvice.watchFromDevices(List.of("banda_actividad")));
        assertNull(RunningAdvice.watchFromDevices(List.of()));
        assertEquals("cinta", RunningAdvice.watch("cinta", ProfileFacts.empty()));
        assertEquals("nada", RunningAdvice.watch(null, ProfileFacts.empty()));
    }

    @Test
    void emptyProfileChangesNothing() {
        ProfileInsights none = ProfileInsights.empty();
        assertTrue(RunningAdvice.rationale(none, false, false, true).isEmpty());
        assertTrue(RunningAdvice.insights(none).isEmpty());
        assertEquals("", RunningAdvice.persona(none));
        assertNull(RunningAdvice.bestHours(none));
    }

    @Test
    void rationaleCitesTheData() {
        ProfileInsights in = insights(Map.of("ub_altitud", "2000_3000", "ub_clima", "calido_humedo",
                "ub_seguridad_noche", "inseguro"), List.of("fc_no_fiable", "riesgo_hipoglucemia"));
        List<Factor> why = RunningAdvice.rationale(in, true, false, true);
        String all = String.join(" | ", why.stream().map(Factor::text).toList());
        assertTrue(all.contains("~2500 m"), all);
        assertTrue(all.contains("4 % más lentos"), all);
        assertTrue(all.contains("2 primeras semanas"), all);
        assertTrue(all.contains("betabloqueadores") && all.contains("RPE"), all);
        assertTrue(all.contains("temprano en la mañana") && all.contains("insegura de noche"), all);
        assertTrue(all.contains("insulina"), all);
        assertTrue(RunningAdvice.persona(in).contains("NUNCA uses zonas de frecuencia cardiaca"));
    }

    @Test
    void measuredAtAltitudeIsNotCorrectedTwice() {
        ProfileInsights in = insights(Map.of("ub_altitud", "mas_3000"), List.of());
        String text = RunningAdvice.rationale(in, false, true, true).get(0).text();
        assertTrue(text.contains("ya incluyen"), text);
    }

    @Test
    void notesOnFirstWeeksAndLongRuns() {
        ProfileInsights in = insights(Map.of("ub_altitud", "2000_3000", "ub_clima", "calido_seco"),
                List.of("riesgo_hipoglucemia"));
        GeneratedPlan plan = RunningPlanBuilder.build(new Input("10k", "10K", Mode.RUN, 10, 0, 4, List.of(1, 3, 5, 6), 6, 15,
                RunningMath.paces(1500).scaled(1.04), 390, true, false, true, RunningPlanBuilderTest.ALL, 10.0, 60, null)).plan();
        GeneratedPlan adapted = RunningAdvice.apply(plan, in, 0, true, false, true);
        assertEquals(plan.sessions().size(), adapted.sessions().size());
        assertTrue(adapted.rationale().size() > plan.rationale().size());
        GeneratedSession first = adapted.sessions().get(0);
        assertTrue(first.notes().contains("Altura"), first.notes());
        // Semana 3 en adelante: sin aviso de aclimatación.
        assertFalse(adapted.sessions().stream().filter(s -> s.weekIndex() >= 2)
                .anyMatch(s -> s.notes() != null && s.notes().contains("Altura")));
        GeneratedSession longRun = adapted.sessions().stream().filter(s -> "tirada_larga".equals(s.kind())).findFirst().orElseThrow();
        assertTrue(longRun.notes().contains("carbohidratos"), longRun.notes());
        assertTrue(longRun.notes().contains("temprano"), longRun.notes());
        // Regenerado a mitad del programa (semana 5): ya no hay aviso de aclimatación.
        GeneratedPlan later = RunningAdvice.apply(plan, in, 5, true, false, true);
        assertFalse(later.sessions().stream().anyMatch(s -> s.notes() != null && s.notes().contains("Altura")));
    }

    @Test
    void missingOptionalAnswersGetExplainedDefaults() {
        List<Factor> runner = RunningWorld.missingDataDefaults(3, null, null);
        assertEquals(2, runner.size());
        assertTrue(runner.get(0).text().contains("Aún no sabemos tus ritmos"));
        assertTrue(runner.get(1).text().contains("~15 km"));
        assertTrue(RunningWorld.missingDataDefaults(3, 20.0, 1500).isEmpty());
        // Caminar-correr no usa ritmos ni km: nada que explicar.
        assertTrue(RunningWorld.missingDataDefaults(1, null, null).isEmpty());
    }
}
