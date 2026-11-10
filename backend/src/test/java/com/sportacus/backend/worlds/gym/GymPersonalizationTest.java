package com.sportacus.backend.worlds.gym;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.catalog.CatalogModels.MovementInfo;
import com.sportacus.backend.world.core.ProfileFacts;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.core.UserProfileData;

class GymPersonalizationTest {

    private static final HealthSummary SCREENED = new HealthSummary(true, "bajo", List.of(), false, false, false,
            Set.of(), List.of(), null);

    private static ProfileInsights insights(boolean eatingCaution, boolean noJumping, boolean lowBudget) {
        ProfileInsights e = ProfileInsights.empty();
        return new ProfileInsights(e.facts(), null, null, false, false, false, eatingCaution, noJumping, lowBudget,
                false, false, false, false, false, false, false, null, null, null, null, List.of(), null, null, null,
                null, null);
    }

    private static UserProfileData profile(List<String> environments, List<String> equipment) {
        return new UserProfileData(null, null, null, null, null, 3, 45, List.of(), environments, equipment, true);
    }

    private static MovementInfo movement(String key, String name, String impact) {
        return new MovementInfo(key, name, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), impact, Map.of(), null, null, null, List.of());
    }

    @Test
    void lugarPrecargadoDesdeElPerfil() {
        assertEquals("gimnasio", GymPersonalization.defaultPlace(profile(List.of("gimnasio", "casa"), List.of())));
        assertEquals("casa_mancuernas", GymPersonalization.defaultPlace(profile(List.of("casa"), List.of("mancuernas"))));
        assertEquals("casa_bandas", GymPersonalization.defaultPlace(profile(List.of(), List.of("bandas"))));
        assertNull(GymPersonalization.defaultPlace(UserProfileData.empty()));
    }

    @Test
    void sinHablarDeGrasaConPrecaucionAlimentaria() {
        assertFalse(GymPersonalization.recompositionOption(true).toLowerCase().contains("grasa"));
        assertTrue(GymPersonalization.recompositionOption(false).toLowerCase().contains("grasa"));
        assertEquals("fuerza y acondicionamiento", GymPersonalization.trackLabel("recomposicion", true));
        List<String> notices = GymPersonalization.notices(SCREENED, insights(true, false, false), "gimnasio", true);
        assertTrue(notices.stream().anyMatch(n -> n.contains("no medimos ni perseguimos cambios de peso")));
        assertTrue(notices.stream().noneMatch(n -> n.toLowerCase().contains("grasa") || n.contains("déficit")));
    }

    @Test
    void saltosSoloSeEvitanEnCasa() {
        ProfileInsights noJump = insights(false, true, false);
        assertTrue(GymPersonalization.avoidJumps(noJump, "casa_bandas"));
        assertFalse(GymPersonalization.avoidJumps(noJump, "gimnasio"));
        assertFalse(GymPersonalization.avoidJumps(ProfileInsights.empty(), "casa_bandas"));
        assertTrue(GymPersonalization.jumpy(movement("sentadilla_salto", "Sentadilla con salto", "medio")));
        assertTrue(GymPersonalization.jumpy(movement("x", "Burpees", "bajo")));
        assertTrue(GymPersonalization.jumpy(movement("x", "Cualquiera", "alto")));
        assertFalse(GymPersonalization.jumpy(movement("sentadilla_goblet", "Sentadilla goblet", "medio")));
        assertTrue(GymPersonalization.warmupCue(true).startsWith("Sin saltos"));
    }

    @Test
    void modoPrudenteSinChequeo() {
        HealthSummary unknown = HealthSummary.unknown();
        assertEquals(3, GymPersonalization.rir(unknown, 1));
        assertEquals(1, GymPersonalization.rir(SCREENED, 1));
        List<String> notices = GymPersonalization.notices(unknown, ProfileInsights.empty(), "gimnasio", false);
        assertTrue(notices.get(0).startsWith("Modo prudente"));
        assertTrue(notices.stream().anyMatch(n -> n.startsWith("Aún no sabemos tus cargas")));
    }

    @Test
    void presupuestoBajoEnCasaSugiereAlternativas() {
        List<String> notices = GymPersonalization.notices(SCREENED, insights(false, false, true), "casa_bandas", true);
        assertTrue(notices.contains(GymPersonalization.budgetTip()));
        assertFalse(GymPersonalization.notices(SCREENED, insights(false, false, true), "gimnasio", true)
                .contains(GymPersonalization.budgetTip()));
    }

    @Test
    void factsVaciosNoCambianNada() {
        ProfileFacts f = new ProfileFacts(Answers.empty(), Map.of());
        assertEquals(0, f.count());
        assertTrue(GymPersonalization.notices(SCREENED, ProfileInsights.empty(), "gimnasio", true).isEmpty());
    }
}
