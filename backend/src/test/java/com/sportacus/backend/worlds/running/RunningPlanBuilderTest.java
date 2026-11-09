package com.sportacus.backend.worlds.running;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.world.core.GeneratedPlan.GeneratedSession;
import com.sportacus.backend.worlds.running.RunningMath.Paces;
import com.sportacus.backend.worlds.running.RunningPlanBuilder.Input;
import com.sportacus.backend.worlds.running.RunningPlanBuilder.Mode;
import com.sportacus.backend.worlds.running.RunningPlanBuilder.Output;

class RunningPlanBuilderTest {

    static final Set<String> ALL = Set.of("movilidad_dinamica_corredor", "caminata_rapida", "caminata_cuestas",
            "caminar_trotar", "trote_suave", "tirada_larga", "carrera_tempo", "intervalos_carrera", "cuestas_carrera",
            "drills_carrera", "zancadas_aceleracion", "estiramiento_gemelo_soleo", "movilidad_cadera_corredor",
            "elevacion_talones", "puente_gluteo", "plancha");
    /** Lo que deja el filtro de seguridad con impacto alto restringido (65+, IMC ≥ 35, lesión leve…). */
    static final Set<String> NO_HIGH = Set.of("movilidad_dinamica_corredor", "caminata_rapida", "caminata_cuestas",
            "caminar_trotar", "trote_suave", "tirada_larga", "drills_carrera", "estiramiento_gemelo_soleo",
            "movilidad_cadera_corredor", "elevacion_talones", "puente_gluteo", "plancha");

    @Test
    void mathFormulas() {
        // Riegel: 25:00 en 5K → ~52:07 en 10K.
        assertEquals(3127, RunningMath.riegel(1500, 5000, 10000), 2);
        // Cooper: 2400 m → VO₂máx ≈ 42,4.
        assertEquals(42.4, RunningMath.cooperVo2(2400), 0.05);
        Paces p = RunningMath.paces(1500); // 5:00 /km
        assertEquals(300, p.race());
        assertEquals(360, p.easyMin());
        assertEquals(390, p.easyMax());
        assertEquals(320, p.tempo());
        assertEquals(297, p.interval());
        assertEquals("5:00", RunningMath.pace(300));
        assertEquals("1:06:40", RunningMath.time(4000));
        assertEquals("6,5", RunningMath.km(6.5));
    }

    @Test
    void volumeRespectsTenPercentRecoveryAndTaper() {
        double[] v = RunningPlanBuilder.volumes("10k", 10, 15);
        for (int w = 1; w < v.length; w++) {
            boolean recovery = (w + 1) % 4 == 0;
            boolean taper = w == v.length - 1;
            if (!recovery && !taper && (w) % 4 != 0) assertTrue(v[w] <= v[w - 1] * 1.1001, "semana " + w);
        }
        assertEquals(v[2] * 0.75, v[3], 0.01); // descarga sobre la última semana de construcción
        assertEquals(0.6, v[9] / v[8], 0.01); // afinamiento
    }

    @Test
    void tenKPlanForFiveKRunner() {
        Output out = RunningPlanBuilder.build(new Input("10k", "10K", Mode.RUN, 10, 0, 4, List.of(1, 3, 5, 6), 6, 15,
                RunningMath.paces(1500), 375, true, false, true, ALL, 10.0, 60, null));
        print(out);
        assertEquals(10, out.plan().weeks());
        assertTrue(out.plan().sessions().size() >= 30);
        // Una tirada larga por semana en sábado y una sesión de calidad (80/20).
        for (int w = 0; w < 9; w++) {
            int week = w;
            List<GeneratedSession> s = out.plan().sessions().stream().filter(x -> x.weekIndex() == week).toList();
            assertEquals(1, s.stream().filter(x -> "tirada_larga".equals(x.kind())).count());
            assertEquals(6, s.stream().filter(x -> "tirada_larga".equals(x.kind())).findFirst().get().weekday());
            assertEquals(1, s.stream().filter(x -> Set.of("intervalos", "tempo", "cuestas").contains(x.kind())).count());
        }
        assertTrue(out.plan().sessions().stream().anyMatch(x -> "prueba".equals(x.kind()) && x.weekIndex() == 9));
        // Regla del 10 % sobre los km reales planificados entre semanas de construcción.
        double prev = out.weekKm().get(0);
        for (int w = 1; w < 9; w++) {
            if ((w + 1) % 4 == 0) continue;
            assertTrue(out.weekKm().get(w) <= prev * 1.1 + 0.3, "semana " + w + ": " + out.weekKm());
            prev = out.weekKm().get(w);
        }
        // Semana 4 (descarga) con menos km que la 3.
        assertTrue(out.weekKm().get(3) < out.weekKm().get(2));
    }

    @Test
    void regenerationMidProgramStartsAtCurrentWeek() {
        Output out = RunningPlanBuilder.build(new Input("10k", "10K", Mode.RUN, 10, 4, 4, List.of(1, 3, 5, 6), 6, 15,
                null, 435, true, false, false, ALL, 10.0, 60, null));
        assertEquals(6, out.plan().weeks());
        assertEquals(0, out.plan().sessions().get(0).weekIndex());
        assertTrue(out.plan().sessions().get(0).notes().startsWith("Semana 5 de 10"));
    }

    @Test
    void couchToFiveK() {
        Output out = RunningPlanBuilder.build(new Input("primer_5k", "Mi primer 5K", Mode.WALK_RUN, 9, 0, 3, List.of(1, 3, 5),
                null, 6, null, 465, false, false, false, ALL, 5.0, 45, null));
        print(out);
        assertEquals(9, out.plan().weeks());
        assertEquals(27, out.plan().sessions().size());
        assertTrue(out.plan().sessions().get(0).title().contains("8×"));
        assertEquals("¡Tus 30 minutos seguidos!", out.plan().sessions().get(26).title());
    }

    @Test
    void restrictedImpactGetsNoHighImpactBlocks() {
        Output out = RunningPlanBuilder.build(new Input("10k", "10K", Mode.RUN, 10, 0, 4, List.of(1, 3, 5, 6), 6, 15,
                RunningMath.paces(1800), 420, false, true, true, NO_HIGH, 10.0, 60, "Sin alto impacto por edad (65+)"));
        assertFalse(out.plan().sessions().stream().flatMap(s -> s.blocks().stream())
                .anyMatch(b -> b.movementKey() != null && !NO_HIGH.contains(b.movementKey())));
        assertTrue(out.plan().sessions().stream().anyMatch(s -> "rodaje_cambios".equals(s.kind())));
    }

    @Test
    void walkOnlyPlan() {
        Output out = RunningPlanBuilder.build(new Input("base_aerobica", "Base aeróbica", Mode.WALK, 6, 0, 3, List.of(1, 3, 5),
                null, 6, null, 465, false, false, false, Set.of("caminata_rapida", "caminata_cuestas", "movilidad_dinamica_corredor",
                        "estiramiento_gemelo_soleo", "movilidad_cadera_corredor", "puente_gluteo", "plancha"), null, 45, "Lesión"));
        print(out);
        assertTrue(out.plan().sessions().stream().allMatch(s -> "caminata".equals(s.kind())));
    }

    @Test
    void halfMarathonTwoWeekTaperAndMaintenance() {
        Output out = RunningPlanBuilder.build(new Input("media_maraton", "Media maratón", Mode.RUN, 12, 0, 5,
                List.of(1, 2, 3, 5, 7), 7, 25, RunningMath.paces(1560), 400, true, false, true, ALL, 21.1, 60, null));
        print(out);
        assertTrue(out.weekKm().get(10) < out.weekKm().get(9));
        Output m = RunningPlanBuilder.build(new Input("media_maraton", "Media maratón", Mode.RUN, 12, 13, 5,
                List.of(1, 2, 3, 5, 7), 7, 25, RunningMath.paces(1560), 400, true, false, true, ALL, 21.1, 60, null));
        assertEquals(1, m.plan().weeks());
    }

    private static void print(Output out) {
        System.out.println("== " + out.plan().title() + " | km/semana " + out.weekKm());
        out.plan().rationale().forEach(f -> System.out.println("   · " + f.text()));
        for (GeneratedSession s : out.plan().sessions()) {
            if (s.weekIndex() > 3 && s.weekIndex() < out.plan().weeks() - 2) continue;
            System.out.println("  S" + s.weekIndex() + " d" + s.weekday() + " [" + s.kind() + "] " + s.title() + " — "
                    + km(RunningPlanBuilder.sessionKm(s, 400)) + " km");
        }
    }

    private static String km(double v) {
        return RunningMath.km(v);
    }
}
