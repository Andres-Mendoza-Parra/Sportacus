package com.sportacus.backend.world.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.catalog.CatalogModels.Contraindication;
import com.sportacus.backend.world.catalog.CatalogModels.MovementInfo;
import com.sportacus.backend.world.core.UserContext;
import com.sportacus.backend.world.core.UserProfileData;

class SafetyFilterTest {

    private final SafetyFilter filter = new SafetyFilter();

    private static MovementInfo movement(String impact, List<Contraindication> contra, List<String> equipment) {
        return new MovementInfo("m", "Movimiento", null, List.of(), List.of(), List.of(), List.of(), List.of(), equipment,
                List.of(), impact, Map.of(), null, null, null, contra);
    }

    private static UserContext user(Set<String> tags, int age) {
        List<HealthSummary.ConditionView> conds = tags.stream()
                .map(t -> new HealthSummary.ConditionView(t, t, "lesion", "moderada", null)).toList();
        HealthSummary h = new HealthSummary(true, "bajo", List.of(), false, false, false, tags, conds, null);
        UserProfileData p = new UserProfileData(Year.now().getValue() - age, null, 70.0, 175.0, 2, 3, 45, List.of(),
                List.of("casa"), List.of("mancuernas"), true);
        return new UserContext(UUID.randomUUID(), "x", p, h);
    }

    @Test
    void contraindicacionEvitarExcluyeDuro() {
        var m = movement("bajo", List.of(new Contraindication("lumbar", "evitar")), List.of());
        assertFalse(filter.allowed(m, user(Set.of("lumbar"), 30)));
        assertTrue(filter.allowed(m, user(Set.of("rodilla"), 30)));
    }

    @Test
    void precaucionNoExcluyePeroAvisa() {
        var m = movement("bajo", List.of(new Contraindication("rodilla", "precaucion")), List.of());
        var u = user(Set.of("rodilla"), 30);
        assertTrue(filter.allowed(m, u));
        assertTrue(filter.cautions(m, u).contains("rodilla"));
    }

    @Test
    void altoImpactoSeExcluyeConLesionDePiernaOEdad() {
        var jump = movement("alto", List.of(), List.of());
        assertFalse(filter.allowed(jump, user(Set.of("tobillo"), 30)));
        assertFalse(filter.allowed(jump, user(Set.of(), 70)));
        assertTrue(filter.allowed(jump, user(Set.of(), 30)));
    }

    @Test
    void equipamientoSegunEntorno() {
        UserProfileData p = new UserProfileData(1990, null, null, null, null, null, null, List.of(), List.of("parque"),
                List.of(), true);
        Set<String> eq = SafetyFilter.availableEquipment(p);
        assertTrue(eq.contains("barra_dominadas"));
        assertFalse(eq.contains("barra_olimpica"));
        assertTrue(SafetyFilter.hasEquipment(movement("bajo", List.of(), List.of("barra_dominadas")), eq));
    }

    @Test
    void sinChequeoDeSaludModoPrudenteSinImpactoAlto() {
        UserProfileData p = new UserProfileData(Year.now().getValue() - 30, null, 70.0, 175.0, 2, 3, 45, List.of(),
                List.of("casa"), List.of(), true);
        UserContext u = new UserContext(UUID.randomUUID(), "x", p, HealthSummary.unknown());
        assertFalse(filter.allowed(movement("alto", List.of(), List.of()), u));
        assertTrue(filter.allowed(movement("medio", List.of(), List.of()), u));
        assertFalse(u.health().vigorousAllowed());
    }
}
