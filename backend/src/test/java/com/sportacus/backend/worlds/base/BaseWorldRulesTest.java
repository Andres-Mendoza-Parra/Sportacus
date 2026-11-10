package com.sportacus.backend.worlds.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.world.core.ProfileFacts;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.core.Widget.Point;

class BaseWorldRulesTest {

    @Test
    void vigorousMinutesCountDouble() {
        assertTrue(BaseWorld.vigorous(7));
        assertFalse(BaseWorld.vigorous(6));
        assertFalse(BaseWorld.vigorous(null));
        assertEquals(90, BaseWorld.whoMinutes(60, 30));
    }

    @Test
    void activityTypeFromTitleOrPlanKind() {
        assertEquals("Bailar", BaseWorld.activityType("Bailar", null));
        assertEquals("Otra", BaseWorld.activityType(null, null));
        assertEquals("Caminar", BaseWorld.activityType("Caminata progresiva", "caminata"));
        assertEquals("Fuerza", BaseWorld.activityType("Fuerza general", "fuerza"));
        assertEquals("running", BaseWorld.worldFor("Correr")[0]);
        assertNull(BaseWorld.worldFor("Bailar"));
    }

    private static ProfileInsights withWeightPref(String pref, boolean eatingCaution) {
        ProfileFacts facts = pref == null ? ProfileFacts.empty()
                : new ProfileFacts(new Answers(Map.of("rc_ver_peso", pref)), Map.of());
        return new ProfileInsights(facts, null, null, false, false, false, eatingCaution, false, false, false, false,
                false, false, false, false, false, null, null, null, null, List.of(), null, null, null, null, null);
    }

    @Test
    void weightVisibilityRespectsPreferenceAndCaution() {
        assertEquals("si", BaseWorld.weightView(withWeightPref(null, false)));
        assertEquals("no", BaseWorld.weightView(withWeightPref("no", false)));
        assertEquals("solo_tendencia", BaseWorld.weightView(withWeightPref("solo_tendencia", false)));
        assertEquals("no", BaseWorld.weightView(withWeightPref("si", true)));
        List<Point> rel = BaseWorld.relativeTrend(List.of(new Point(LocalDate.of(2026, 1, 1), 80),
                new Point(LocalDate.of(2026, 2, 1), 78.5)));
        assertEquals(0, rel.get(0).value());
        assertEquals(-1.5, rel.get(1).value());
    }
}
