package com.sportacus.backend.worlds.calisthenics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.core.ProfileFacts;
import com.sportacus.backend.world.core.UserContext;
import com.sportacus.backend.world.core.UserProfileData;

/** Onboarding mínimo y uso del Perfil 360 en Calistenia. */
class CalisthenicsOnboardingTest {

    @Test
    void marcasDesconocidasSeDetectanPorArbol() {
        Set<String> answered = Set.of("c_pushups", "c_handstand");
        List<String> unknown = CalisthenicsRules.unknownMarkTrees(List.of("empuje", "jale", "core", "parada_manos"),
                answered::contains);
        assertEquals(List.of("jale", "core"), unknown);
        assertNull(CalisthenicsRules.unknownMarksNotice(List.of()));
        String notice = CalisthenicsRules.unknownMarksNotice(List.of("Jale", "Core"));
        assertTrue(notice.startsWith("Aún no sabemos tus marcas en Jale, Core"));
        assertTrue(notice.contains("Haz el test"));
    }

    @Test
    void sinMarcasSeEmpiezaEnEscalonBaseSeguro() {
        CalisthenicsRules.Maxes none = new CalisthenicsRules.Maxes(0, 0, 0, 0, 0, "nunca");
        CalisthenicsRules.Flags f = CalisthenicsRules.Flags.none();
        assertEquals(1, CalisthenicsRules.place("empuje", none, f).step()); // flexión inclinada
        assertEquals(0, CalisthenicsRules.place("jale", none, f).step()); // colgado
        assertEquals(0, CalisthenicsRules.place("piernas", none, f).step());
        assertEquals(0, CalisthenicsRules.place("core", none, f).step());
        assertEquals(0, CalisthenicsRules.place("parada_manos", none, f).step());
    }

    @Test
    void equipoYLugarSalenDelPerfil() {
        assertEquals(List.of("barra_dominadas", "bandas"),
                CalisthenicsRules.homeEquipmentFromProfile(List.of("mancuernas", "barra_dominadas", "bandas")));
        assertEquals(List.of("ninguno"), CalisthenicsRules.homeEquipmentFromProfile(List.of("mancuernas")));
        assertEquals(List.of(), CalisthenicsRules.homeEquipmentFromProfile(List.of()));
        assertEquals("ambos", CalisthenicsRules.placeFromEnvironments(List.of("casa", "parque")));
        assertEquals("parque", CalisthenicsRules.placeFromEnvironments(List.of("parque", "gimnasio")));
        assertNull(CalisthenicsRules.placeFromEnvironments(List.of("gimnasio")));
    }

    @Test
    void precargaConHijosPequenosSugiereSesionesCortas() {
        UserProfileData profile = new UserProfileData(1990, null, null, null, null, 3, 45, List.of(),
                List.of("casa"), List.of("barra_dominadas"), true);
        // Perfil 360 con hijos pequeños (so_hijos = si_pequenos).
        ProfileInsights insights = new ProfileInsights(ProfileFacts.empty(), null, null, false, false, false, false,
                false, false, false, false, false, true, false, false, false, null, null, null, null, List.of(), null,
                null, null, null, null);
        UserContext user = new UserContext(UUID.randomUUID(), "Ana", profile, HealthSummary.unknown(), insights);
        Map<String, Object> pre = CalisthenicsWorld.prefill(user);
        assertEquals("casa", pre.get("c_place"));
        assertEquals(List.of("barra_dominadas"), pre.get("c_equipment"));
        assertEquals("si", pre.get("c_short"));

        UserContext plain = new UserContext(UUID.randomUUID(), "Leo", UserProfileData.empty(), HealthSummary.unknown());
        assertTrue(CalisthenicsWorld.prefill(plain).isEmpty());
    }

    @Test
    void sesionesExpresRecortanMinutos() {
        assertEquals(25, CalisthenicsRules.sessionMinutes(60, true));
        assertEquals(20, CalisthenicsRules.sessionMinutes(20, true));
        assertEquals(60, CalisthenicsRules.sessionMinutes(60, false));
        assertEquals(20, CalisthenicsRules.sessionMinutes(10, false));
    }

    @Test
    void sinSaltosEnCasa() {
        assertTrue(CalisthenicsRules.jumpy("burpees", "Burpees"));
        assertTrue(CalisthenicsRules.jumpy("x", "Sentadilla con salto"));
        assertFalse(CalisthenicsRules.jumpy("flexion_pared", "Flexión en la pared"));
        assertTrue(CalisthenicsRules.quietCue("dominada_negativa").contains("silla"));
        assertTrue(CalisthenicsRules.quietCue("parada_manos_intentos").contains("pared"));
        assertNull(CalisthenicsRules.quietCue("flexion_inclinada"));
    }

    @Test
    void modoPrudenteExplicaElChequeo() {
        assertTrue(HealthSummary.unknown().moderateReason().startsWith("Modo prudente"));
    }
}
