package com.sportacus.backend.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.profile.bank.BankModels.BankOption;
import com.sportacus.backend.profile.bank.BankModels.BankQuestion;
import com.sportacus.backend.world.core.ProfileFacts;

/** Reglas puras del Perfil 360: validación de respuestas y visibilidad condicional. */
class Profile360RulesTest {

    static BankQuestion q(String id, String type, Map<String, Object> showIf, String... options) {
        List<BankOption> opts = options.length == 0 ? null
                : Arrays.stream(options).map(o -> new BankOption(o, null, o, null)).toList();
        Integer min = type.equals("range") ? 1 : null;
        Integer max = type.equals("range") ? 5 : null;
        return new BankQuestion(id, type, id, null, opts, min, max, 1, null, null, "porque sí", List.of("plan"), 3,
                null, null, null, showIf, null);
    }

    @Test
    void validaRespuestasContraLaDefinicion() {
        BankQuestion opts = q("o", "options", null, "a", "b");
        assertEquals("a", Profile360Service.validate(opts, "a"));
        assertThrows(ResponseStatusException.class, () -> Profile360Service.validate(opts, "zzz"));
        BankQuestion multi = q("m", "multi", null, "a", "b");
        assertEquals(List.of("a", "b"), Profile360Service.validate(multi, List.of("a", "b")));
        BankQuestion range = q("r", "range", null);
        assertEquals(3, Profile360Service.validate(range, "3"));
        assertThrows(ResponseStatusException.class, () -> Profile360Service.validate(range, 9));
        BankQuestion date = q("d", "date", null);
        assertEquals("2027-03-01", Profile360Service.validate(date, "2027-03-01"));
        assertThrows(ResponseStatusException.class, () -> Profile360Service.validate(date, "01/03/2027"));
    }

    @Test
    void visibilidadPorShowIf() {
        BankQuestion child = q("so_perro_paseos", "range", Map.of("question", "so_mascota", "in", List.of("perro")));
        ProfileFacts dog = new ProfileFacts(new Answers(Map.of("so_mascota", List.of("gato", "perro"))), Map.of());
        assertFalse(Profile360Service.visible(child, ProfileFacts.empty()));
        assertTrue(Profile360Service.visible(child, dog));
    }
}
