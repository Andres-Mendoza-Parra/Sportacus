package com.sportacus.backend.world.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.profile.ProfileForm;

class EnrollmentMergeTest {

    @Test
    void escalonesConservanElMayorYNoSeBorran() {
        // Caso de la auditoría (A1): 5/3/3/3 no debe volver a 2/0/0/0 al reenviar el onboarding.
        Map<String, Integer> current = Map.of("cal_empuje", 5, "cal_jale", 3, "cal_piernas", 3, "cal_core", 3);
        Map<String, Integer> fresh = Map.of("cal_empuje", 2, "cal_jale", 0, "cal_piernas", 0, "cal_core", 0,
                "cal_parada_manos", 1);
        Map<String, Integer> out = EnrollmentMerge.progressions(current, fresh);
        assertEquals(5, out.get("cal_empuje"));
        assertEquals(3, out.get("cal_jale"));
        assertEquals(3, out.get("cal_core"));
        assertEquals(1, out.get("cal_parada_manos"));
        assertEquals(Map.of("a", 4), EnrollmentMerge.progressions(Map.of("a", 4), Map.of()));
        assertEquals(Map.of("a", 6), EnrollmentMerge.progressions(Map.of("a", 4), Map.of("a", 6)));
    }

    @Test
    void respuestasNuevasReemplazanYLasDemasSeConservan() {
        Map<String, Object> prev = Map.of("g_goal", "fuerza", "g_place", "gimnasio", "g_5rm", 80);
        Map<String, Object> fresh = new LinkedHashMap<>();
        fresh.put("g_place", "casa");
        fresh.put("g_extra", "x");
        fresh.put("g_goal", null);
        Map<String, Object> out = EnrollmentMerge.answers(prev, fresh);
        assertEquals("fuerza", out.get("g_goal"));
        assertEquals("casa", out.get("g_place"));
        assertEquals(80, out.get("g_5rm"));
        assertEquals("x", out.get("g_extra"));
    }

    @Test
    void estadoVivoSeConservaSalvoLoQueCambianLasRespuestas() {
        Map<String, Object> stored = Map.of("loads", Map.of("sentadilla", 100), "place", "gimnasio", "phase", 2,
                "planStart", "2026-10-01");
        Map<String, Object> fromPrevious = Map.of("loads", Map.of("sentadilla", 60), "place", "gimnasio", "phase", 0,
                "planStart", "2026-11-09");
        Map<String, Object> fromFresh = Map.of("loads", Map.of("sentadilla", 60), "place", "casa", "phase", 0,
                "planStart", "2026-11-09", "nuevo", true);
        Map<String, Object> out = EnrollmentMerge.state(stored, fromPrevious, fromFresh);
        assertEquals(Map.of("sentadilla", 100), out.get("loads")); // cargas ganadas entrenando
        assertEquals(2, out.get("phase"));
        assertEquals("2026-10-01", out.get("planStart"));
        assertEquals("casa", out.get("place")); // la respuesta editada sí cambia
        assertEquals(true, out.get("nuevo"));
    }

    @Test
    void validaLasRespuestasDelPerfilContraLaDefinicion() {
        var form = ProfileForm.form();
        assertThrows(ResponseStatusException.class,
                () -> FormAnswersValidator.validate(form, new Answers(Map.of("p_days", "abc"))));
        assertThrows(ResponseStatusException.class,
                () -> FormAnswersValidator.validate(form, new Answers(Map.of("p_days", 9))));
        assertThrows(ResponseStatusException.class,
                () -> FormAnswersValidator.validate(form, new Answers(Map.of("p_minutes", "25"))));
        assertThrows(ResponseStatusException.class,
                () -> FormAnswersValidator.validate(form, new Answers(Map.of("p_sex", "x"))));
        assertThrows(ResponseStatusException.class,
                () -> FormAnswersValidator.validate(form, new Answers(Map.of("p_environments", List.of("luna")))));
        Answers ok = FormAnswersValidator.validate(form,
                new Answers(Map.of("p_days", "3", "p_minutes", 30, "p_environments", List.of("casa", "parque"))));
        assertEquals(3, ok.integer("p_days"));
        assertEquals("30", ok.string("p_minutes"));
        assertEquals(List.of("casa", "parque"), ok.list("p_environments"));
    }
}
