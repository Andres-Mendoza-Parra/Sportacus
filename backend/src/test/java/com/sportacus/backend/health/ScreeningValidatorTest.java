package com.sportacus.backend.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ScreeningValidatorTest {

    private static Map<String, Object> complete() {
        Map<String, Object> m = new HashMap<>();
        m.put("s_active", "si");
        m.put("s_diagnosis", List.of("ninguna"));
        m.put("s_symptoms", List.of("ninguno"));
        m.put("s_red_flags", List.of("ninguna"));
        m.put("s_pain_zones", List.of("ninguna"));
        m.put("s_surgery", "no");
        m.put("s_medications", List.of("ninguno"));
        m.put("s_mobility", List.of("ninguna"));
        m.put("s_pregnancy", "no_aplica");
        return m;
    }

    @Test
    void vacioNoEsUnChequeo() {
        assertThrows(ScreeningValidator.Invalid.class, () -> ScreeningValidator.clean(HealthScreeningForm.form(), Map.of()));
    }

    @Test
    void completoPasa() {
        Map<String, Object> out = ScreeningValidator.clean(HealthScreeningForm.form(), complete());
        assertEquals("si", out.get("s_active"));
        assertFalse(out.containsKey("s_pain_severity")); // oculta: no hay zonas con dolor
    }

    @Test
    void opcionInexistente() {
        Map<String, Object> m = complete();
        m.put("s_symptoms", List.of("inventado"));
        assertThrows(ScreeningValidator.Invalid.class, () -> ScreeningValidator.clean(HealthScreeningForm.form(), m));
    }

    @Test
    void ningunaNoSeCombina() {
        Map<String, Object> m = complete();
        m.put("s_symptoms", List.of("ninguno", "mareos"));
        assertThrows(ScreeningValidator.Invalid.class, () -> ScreeningValidator.clean(HealthScreeningForm.form(), m));
    }

    @Test
    void condicionalesObligatoriasCuandoSonVisibles() {
        Map<String, Object> m = complete();
        m.put("s_pain_zones", List.of("rodilla"));
        assertThrows(ScreeningValidator.Invalid.class, () -> ScreeningValidator.clean(HealthScreeningForm.form(), m));
        m.put("s_pain_severity", "moderada");
        assertEquals("moderada", ScreeningValidator.clean(HealthScreeningForm.form(), m).get("s_pain_severity"));
    }

    @Test
    void descartaClavesAjenasYOcultas() {
        Map<String, Object> m = complete();
        m.put("x_otra", "valor");
        m.put("s_clearance", "si"); // oculta porque no hubo cirugía
        Map<String, Object> out = ScreeningValidator.clean(HealthScreeningForm.form(), m);
        assertFalse(out.containsKey("x_otra"));
        assertFalse(out.containsKey("s_clearance"));
    }
}
