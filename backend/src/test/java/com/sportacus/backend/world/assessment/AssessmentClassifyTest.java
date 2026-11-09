package com.sportacus.backend.world.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Year;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.world.catalog.CatalogModels.AssessmentInfo;
import com.sportacus.backend.world.core.UserProfileData;

class AssessmentClassifyTest {

    private static AssessmentInfo test(Map<String, Object> norms) {
        return new AssessmentInfo("t", "w", "Test", "d", List.of(), "count", null, "m", "reps", 28, norms, null, null, 0);
    }

    private static UserProfileData person(String sex, int age) {
        return new UserProfileData(Year.now().getValue() - age, sex, null, null, null, null, null, List.of(), List.of(),
                List.of(), true);
    }

    @Test
    void bandasSimples() {
        var a = test(Map.of("bands", List.of(Map.of("max", 10, "label", "Bajo"), Map.of("min", 10, "label", "Alto"))));
        assertEquals("Bajo", AssessmentService.classify(a, 9, person(null, 30)));
        assertEquals("Alto", AssessmentService.classify(a, 10, person(null, 30)));
    }

    @Test
    void gruposPorSexoYEdad() {
        var a = test(Map.of("groups", List.of(
                Map.of("sex", "femenino", "ageMin", 65, "ageMax", 69,
                        "bands", List.of(Map.of("max", 11, "label", "Bajo el promedio"), Map.of("min", 11, "label", "Promedio o mejor"))),
                Map.of("sex", "masculino", "ageMin", 65, "ageMax", 69,
                        "bands", List.of(Map.of("max", 12, "label", "Bajo el promedio"), Map.of("min", 12, "label", "Promedio o mejor"))))));
        assertEquals("Bajo el promedio", AssessmentService.classify(a, 10, person("femenino", 67)));
        assertEquals("Promedio o mejor", AssessmentService.classify(a, 11, person("femenino", 67)));
        assertEquals("Bajo el promedio", AssessmentService.classify(a, 11, person("masculino", 67)));
    }

    @Test
    void sinNormasNoClasifica() {
        assertNull(AssessmentService.classify(test(Map.of()), 5, person(null, 30)));
    }
}
