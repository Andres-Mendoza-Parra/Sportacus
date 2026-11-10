package com.sportacus.backend.worlds.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.core.GeneratedPlan;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedBlock;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedSession;
import com.sportacus.backend.world.core.ProfileFacts;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.core.UserProfileData;

class BasePlanBuilderTest {

    /** Todos los movimientos de content/worlds/base.json. */
    static final Map<String, String> ALL = new LinkedHashMap<>();
    static {
        Stream.of("base_remo_mochila", "hea_respiracion_diafragmatica", "hea_movilidad_articular_suave",
                "hea_relajacion_guiada", "hea_caminata_suave", "hea_caminata_rapida", "hea_marcha_estatica",
                "hea_sentarse_levantarse", "flexion_inclinada", "hea_flexion_pared", "puente_gluteo",
                "hea_puente_gluteo", "remo_bajo_mesa", "superman_y", "dead_bug", "elevacion_talones",
                "hea_equilibrio_un_pie_apoyo", "hea_tandem_apoyo", "hea_apertura_toracica_silla",
                "hea_estiramiento_flexores_cadera", "hea_rotacion_tronco_sentado", "hea_retraccion_escapular",
                "hea_estiramiento_pecho_pared", "hea_gato_camello").forEach(k -> ALL.put(k, k));
    }

    static final Set<String> STRENGTH_KEYS = Set.of("hea_sentarse_levantarse", "flexion_inclinada", "hea_flexion_pared",
            "puente_gluteo", "hea_puente_gluteo", "base_remo_mochila", "remo_bajo_mesa", "superman_y", "dead_bug",
            "elevacion_talones");

    static ProfileInsights insights(boolean dog, boolean noJump, boolean children, boolean physicalJob,
            Integer sitting, Integer commute) {
        return new ProfileInsights(ProfileFacts.empty(), null, null, false, false, false, false, noJump, false, false,
                false, dog, children, false, false, physicalJob, sitting, commute, null, null, List.of(), null, null,
                null, null, null);
    }

    static GeneratedPlan plan(UserProfileData p, HealthSummary h, ProfileInsights i) {
        return BasePlanBuilder.build(new BasePlanBuilder.Input(p, h, i, ALL, List.of(1, 3, 5, 6, 7, 2, 4), 0));
    }

    static Stream<GeneratedBlock> blocks(GeneratedPlan plan) {
        return plan.sessions().stream().flatMap(s -> s.blocks().stream());
    }

    static String rationale(GeneratedPlan plan) {
        return String.join(" | ", plan.rationale().stream().map(f -> f.text()).toList());
    }

    static UserProfileData profile(Integer birthYear, Integer days, Integer minutes) {
        return new UserProfileData(birthYear, null, null, null, null, days, minutes, List.of(), List.of(), List.of(), false);
    }

    @Test
    void emptyProfileGivesThreePrudentSessions() {
        GeneratedPlan p = plan(UserProfileData.empty(), HealthSummary.unknown(), ProfileInsights.empty());
        assertEquals(3, p.sessions().size());
        assertEquals(List.of("fuerza", "caminata", "fuerza"), p.sessions().stream().map(GeneratedSession::kind).toList());
        assertTrue(rationale(p).contains("empezamos con 3"));
        assertTrue(rationale(p).contains("20 min"));
        assertTrue(rationale(p).contains("chequeo de salud"));
        // Sesiones cortas (~20 min) y sin intensidad alta.
        p.sessions().forEach(s -> assertTrue(s.estimatedMinutes() <= 26, s.title() + " dura " + s.estimatedMinutes()));
        blocks(p).forEach(b -> {
            String i = b.prescription().intensity();
            assertTrue(i == null || !i.contains("intens") && !i.contains("RPE 7"), i);
        });
        assertTrue(p.sessions().stream().allMatch(s -> s.notes() != null && !s.notes().isBlank()));
        assertEquals(List.of(1, 3, 5), p.sessions().stream().map(GeneratedSession::weekday).toList());
    }

    @Test
    void dogTurnsWalksIntoDogWalks() {
        GeneratedPlan p = plan(profile(null, 4, 30), HealthSummary.unknown(), insights(true, false, false, false, null, null));
        List<GeneratedSession> walks = p.sessions().stream().filter(s -> "caminata".equals(s.kind())).toList();
        assertFalse(walks.isEmpty());
        walks.forEach(s -> {
            assertTrue(s.notes().contains("Como tienes perro"), s.notes());
            assertEquals("Caminata con tu perro", s.title());
        });
        assertTrue(rationale(p).contains("perro"));
        assertTrue(rationale(p).contains("4 días"));
    }

    @Test
    void mustReferOnlyGentle() {
        HealthSummary red = new HealthSummary(true, "alto", List.of(), false, false, true, Set.of(), List.of(), null);
        GeneratedPlan p = plan(profile(null, 5, 60), red, ProfileInsights.empty());
        assertTrue(p.sessions().stream().allMatch(s -> "suave".equals(s.kind())));
        assertTrue(blocks(p).noneMatch(b -> STRENGTH_KEYS.contains(b.movementKey())));
        assertTrue(blocks(p).noneMatch(b -> "hea_caminata_rapida".equals(b.movementKey())));
        p.sessions().forEach(s -> assertTrue(s.estimatedMinutes() <= 16, "dura " + s.estimatedMinutes()));
        assertTrue(rationale(p).contains("profesional"));
    }

    @Test
    void noJumpingHasNoJumps() {
        GeneratedPlan p = plan(profile(null, 5, 45), HealthSummary.unknown(), insights(false, true, false, false, null, null));
        assertTrue(blocks(p).noneMatch(b -> b.movementKey().contains("salto") || b.movementKey().contains("jump")));
        assertTrue(rationale(p).contains("sin saltos") || rationale(p).contains("no tiene saltos"));
        p.sessions().stream().filter(s -> "fuerza".equals(s.kind()))
                .forEach(s -> assertTrue(s.notes().contains("Sin saltos")));
    }

    @Test
    void strengthTwiceAWeekAtMost() {
        GeneratedPlan p = plan(profile(null, 7, 30), HealthSummary.unknown(), ProfileInsights.empty());
        assertEquals(7, p.sessions().size());
        assertEquals(2, p.sessions().stream().filter(s -> "fuerza".equals(s.kind())).count());
    }

    @Test
    void olderAdultsGetBalance() {
        int year = java.time.Year.now().getValue() - 70;
        GeneratedPlan p = plan(profile(year, 3, 30), HealthSummary.unknown(), ProfileInsights.empty());
        long withBalance = p.sessions().stream().filter(s -> s.blocks().stream().anyMatch(
                b -> b.movementKey().startsWith("hea_equilibrio") || b.movementKey().startsWith("hea_tandem"))).count();
        assertEquals(3, withBalance);
        assertTrue(rationale(p).contains("70 años"));
    }

    @Test
    void youngChildrenShortHomeSessions() {
        GeneratedPlan p = plan(profile(null, 3, 60), HealthSummary.unknown(), insights(false, false, true, false, null, null));
        p.sessions().forEach(s -> assertTrue(s.estimatedMinutes() <= 26, "dura " + s.estimatedMinutes()));
        assertTrue(blocks(p).noneMatch(b -> "hea_caminata_rapida".equals(b.movementKey())));
        assertTrue(p.sessions().stream().filter(s -> "caminata".equals(s.kind()))
                .allMatch(s -> s.notes().contains("hijos pequeños")));
    }

    @Test
    void sittingAddsMobilityAndCommuteCounts() {
        GeneratedPlan p = plan(profile(null, 4, 30), HealthSummary.unknown(), insights(false, false, false, false, 9, 100));
        assertTrue(p.sessions().stream().anyMatch(s -> "movilidad".equals(s.kind()) && s.notes().contains("9 h sentado")));
        assertTrue(rationale(p).contains("100 min"));
        assertTrue(rationale(p).contains("te faltan ~50"));
    }

    @Test
    void physicalJobLessVolume() {
        GeneratedPlan p = plan(profile(null, 3, 60), HealthSummary.unknown(), insights(false, false, false, true, null, null));
        blocks(p).filter(b -> "principal".equals(b.blockType()) && "reps".equals(b.prescription().type()))
                .forEach(b -> assertEquals(1, b.prescription().sets()));
        assertTrue(blocks(p).anyMatch(b -> "hea_estiramiento_flexores_cadera".equals(b.movementKey())));
    }

    @Test
    void missingMovementsAreSkippedOrReplaced() {
        Map<String, String> few = new LinkedHashMap<>(ALL);
        few.remove("flexion_inclinada");
        few.remove("base_remo_mochila");
        GeneratedPlan p = BasePlanBuilder.build(new BasePlanBuilder.Input(UserProfileData.empty(), HealthSummary.unknown(),
                ProfileInsights.empty(), few, List.of(), 0));
        assertTrue(blocks(p).anyMatch(b -> "hea_flexion_pared".equals(b.movementKey())));
        assertTrue(blocks(p).anyMatch(b -> "remo_bajo_mesa".equals(b.movementKey())));
        assertTrue(blocks(p).allMatch(b -> few.containsKey(b.movementKey())));
    }

    @Test
    void walksGrowWeekByWeek() {
        BasePlanBuilder.Input w0 = new BasePlanBuilder.Input(profile(null, 2, 40), HealthSummary.unknown(),
                ProfileInsights.empty(), ALL, List.of(), 0);
        BasePlanBuilder.Input w3 = new BasePlanBuilder.Input(profile(null, 2, 40), HealthSummary.unknown(),
                ProfileInsights.empty(), ALL, List.of(), 3);
        assertTrue(walkSeconds(BasePlanBuilder.build(w3)) > walkSeconds(BasePlanBuilder.build(w0)));
    }

    static int walkSeconds(GeneratedPlan p) {
        return blocks(p).filter(b -> b.movementKey().startsWith("hea_caminata"))
                .mapToInt(b -> b.prescription().durationS()).sum();
    }
}
