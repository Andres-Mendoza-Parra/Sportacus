package com.sportacus.backend.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.common.Answers;

class ScreeningRulesTest {

    private static Map<String, Object> base() {
        Map<String, Object> m = new HashMap<>();
        m.put("s_active", "si");
        m.put("s_diagnosis", List.of("ninguna"));
        m.put("s_symptoms", List.of("ninguno"));
        m.put("s_red_flags", List.of("ninguna"));
        m.put("s_pain_zones", List.of("ninguna"));
        m.put("s_surgery", "no");
        m.put("s_pregnancy", "no");
        return m;
    }

    @Test
    void personaActivaSinNadaEsRiesgoBajo() {
        var r = ScreeningRules.evaluate(new Answers(base()));
        assertEquals("bajo", r.riskLevel());
        assertFalse(r.requiresClearance());
        assertFalse(r.redFlags());
    }

    @Test
    void sintomasCardiovascularesExigenAvalYRiesgoAlto() {
        var m = base();
        m.put("s_symptoms", List.of("dolor_pecho"));
        var r = ScreeningRules.evaluate(new Answers(m));
        assertEquals("alto", r.riskLevel());
        assertTrue(r.requiresClearance());
        assertTrue(r.flags().contains("cardio_sintomas"));
    }

    @Test
    void banderaRojaSeMarcaYDeriva() {
        var m = base();
        m.put("s_red_flags", List.of("esfinteres"));
        var r = ScreeningRules.evaluate(new Answers(m));
        assertTrue(r.redFlags());
        assertTrue(r.flags().contains("bandera_roja_esfinteres"));
        assertEquals("alto", r.riskLevel());
    }

    @Test
    void enfermedadMetabolicaEInactivoRequiereAval() {
        var m = base();
        m.put("s_active", "no");
        m.put("s_diagnosis", List.of("diabetes"));
        var r = ScreeningRules.evaluate(new Answers(m));
        assertEquals("moderado", r.riskLevel());
        assertTrue(r.requiresClearance());
    }

    @Test
    void enfermedadMetabolicaYActivoPuedeSeguirModerado() {
        var m = base();
        m.put("s_diagnosis", List.of("hipertension"));
        var r = ScreeningRules.evaluate(new Answers(m));
        assertFalse(r.requiresClearance());
        assertTrue(r.flags().contains("vigorosa_requiere_aval"));
    }

    @Test
    void cirugiaSinAvalRequiereAval() {
        var m = base();
        m.put("s_surgery", "si");
        m.put("s_clearance", "no");
        assertTrue(ScreeningRules.evaluate(new Answers(m)).requiresClearance());
        m.put("s_clearance", "si");
        assertFalse(ScreeningRules.evaluate(new Answers(m)).requiresClearance());
    }
}
