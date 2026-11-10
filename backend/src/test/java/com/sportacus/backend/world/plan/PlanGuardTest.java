package com.sportacus.backend.world.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.world.core.GeneratedPlan;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedBlock;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedSession;
import com.sportacus.backend.world.core.Prescription;

class PlanGuardTest {

    private static final Map<String, String> IMPACT = Map.of("sentadilla", "medio", "salto", "alto", "plancha", "bajo",
            "remo", "bajo", "hea_respiracion_diafragmatica", "bajo", "hea_movilidad_articular_suave", "bajo",
            "hea_caminata_suave", "bajo", "hea_relajacion_guiada", "bajo", "movilidad", "bajo");

    private static PlanGuard.Inputs in(int days, int minutes, boolean refer, boolean vigorous, boolean minor) {
        return new PlanGuard.Inputs("gym", days, minutes, refer, vigorous, minor, IMPACT::get);
    }

    private static GeneratedSession session(int day, String kind, GeneratedBlock... blocks) {
        return new GeneratedSession(0, day, day + 1, "S" + day, null, kind, null, null, List.of(blocks));
    }

    private static GeneratedPlan plan(GeneratedSession... s) {
        return new GeneratedPlan("P", null, 1, null, List.of(), List.of(s));
    }

    private static GeneratedBlock heavy() {
        return GeneratedBlock.of("principal", "sentadilla", null, Prescription.reps(4, 3, 6, 180).withRir(1).withLoad(100.0));
    }

    @Test
    void diasEstrictosSinContarPartidoNiEquipo() {
        GeneratedPlan p = plan(session(0, "fuerza", heavy()), session(1, "fuerza", heavy()), session(2, "partido"),
                session(3, "fuerza", heavy()), session(4, "equipo"));
        GeneratedPlan out = PlanGuard.apply(p, in(1, 90, false, true, false));
        assertEquals(3, out.sessions().size());
        assertEquals(1, out.sessions().stream().filter(s -> "fuerza".equals(s.kind())).count());
    }

    @Test
    void minutosEstrictosQuitaAccesoriosYBajaSeries() {
        List<GeneratedBlock> blocks = new ArrayList<>();
        blocks.add(GeneratedBlock.of("calentamiento", "movilidad", null, Prescription.time(300, "suave")));
        for (int i = 0; i < 4; i++) blocks.add(GeneratedBlock.of("principal", "sentadilla", null, Prescription.reps(4, 8, 12, 120)));
        blocks.add(GeneratedBlock.of("accesorio", "plancha", null, Prescription.hold(3, 40, 60)));
        GeneratedSession s = new GeneratedSession(0, 0, 1, "Larga", null, "fuerza", 70, null, blocks);
        GeneratedSession out = PlanGuard.capMinutes(s, 20);
        assertTrue(out.estMinutes() <= 20, "estimado " + out.estMinutes());
        assertTrue(PlanGuard.minutesOf(out.blocks()) <= 20);
        assertTrue(out.blocks().stream().noneMatch(b -> "accesorio".equals(b.blockType())));
        assertTrue(out.blocks().stream().anyMatch(b -> "principal".equals(b.blockType())));
        assertTrue(out.notes().contains("20 minutos"));
    }

    @Test
    void escalonDeProgresionConservaTresSeries() {
        GeneratedBlock step = GeneratedBlock.of("principal", "sentadilla", null, Prescription.reps(3, 12, 60))
                .withProgression("cal_piernas", 2);
        GeneratedBlock extra = GeneratedBlock.of("principal", "remo", null, Prescription.reps(4, 10, 90));
        GeneratedSession out = PlanGuard.capMinutes(new GeneratedSession(0, 0, 1, "C", null, "skills", 40, null,
                List.of(step, extra)), 10);
        GeneratedBlock kept = out.blocks().stream().filter(b -> b.progressionKey() != null).findFirst().orElseThrow();
        assertEquals(3, kept.prescription().sets());
    }

    @Test
    void sesionCortaNoSeToca() {
        GeneratedSession s = session(0, "fuerza", GeneratedBlock.of("principal", "remo", null, Prescription.reps(2, 10, 60)));
        GeneratedSession out = PlanGuard.capMinutes(s, 30);
        assertEquals(s.blocks(), out.blocks());
        assertNull(out.notes());
    }

    @Test
    void derivacionMedicaSoloSesionesSuaves() {
        GeneratedPlan p = plan(session(0, "fuerza", heavy(), GeneratedBlock.of("principal", "salto", null,
                Prescription.interval(6, 20, 40, "máxima"))));
        GeneratedSession s = PlanGuard.apply(p, in(3, 45, true, false, false)).sessions().get(0);
        assertEquals("Sesión suave", s.title());
        assertTrue(s.blocks().stream().allMatch(b -> "bajo".equals(IMPACT.get(b.movementKey()))));
        assertTrue(s.notes().contains("profesional"));
    }

    @Test
    void sinAvalModeraIntensidadYCargas() {
        GeneratedPlan p = plan(session(0, "fuerza", heavy(), GeneratedBlock.of("principal", "remo", null,
                Prescription.interval(6, 20, 40, "máxima"))));
        GeneratedSession s = PlanGuard.apply(p, in(3, 60, false, false, false)).sessions().get(0);
        Prescription sq = s.blocks().get(0).prescription();
        assertEquals(8, sq.repsMin());
        assertTrue(sq.rir() >= 2);
        assertNull(sq.loadKg());
        assertFalse(PlanGuard.isVigorous(s.blocks().get(1).prescription().intensity()));
    }

    @Test
    void menoresSinCargasMaximas() {
        GeneratedSession s = PlanGuard.apply(plan(session(0, "fuerza", heavy())), in(3, 60, false, true, true))
                .sessions().get(0);
        assertEquals(8, s.blocks().get(0).prescription().repsMin());
        assertNotNull(s.notes());
    }

    @Test
    void huellaDetectaQueCambioYLoExplica() {
        Map<String, Object> before = Map.of("salud", "a", "perfil", "b", "perfil360", "c", "mundo", "d", "semana", "-",
                "contenido", "x");
        Map<String, String> after = Map.of("salud", "z", "perfil", "b", "perfil360", "c", "mundo", "d", "semana", "-",
                "contenido", "x");
        assertEquals("Actualizamos tu plan porque actualizaste tu chequeo de salud.", PlanInputs.reason(before, after));
        assertNull(PlanInputs.reason(Map.of(), after));
    }
}
