package com.sportacus.backend.profile.bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.profile.bank.BankModels.Area;
import com.sportacus.backend.profile.bank.BankModels.AreaFile;
import com.sportacus.backend.profile.bank.BankModels.BankOption;
import com.sportacus.backend.profile.bank.BankModels.BankQuestion;
import com.sportacus.backend.profile.bank.BankModels.Questionnaire;

class QuestionBankTest {

    static BankQuestion q(String id, String type, Map<String, Object> showIf, String... options) {
        List<BankOption> opts = options.length == 0 ? null
                : Arrays.stream(options).map(o -> new BankOption(o, null, o, null)).toList();
        Integer min = type.equals("range") ? 1 : null;
        Integer max = type.equals("range") ? 5 : null;
        return new BankQuestion(id, type, id, null, opts, min, max, 1, null, null, "porque sí", List.of("plan"), 3,
                null, null, null, showIf, null);
    }

    static AreaFile area(String key, boolean sensitive, BankQuestion... qs) {
        return new AreaFile(new Area(key, key, null, null, 1, sensitive),
                List.of(new Questionnaire(key + "_q", key, null, 1, "sportacus", List.of(qs))), List.of());
    }

    @Test
    void cargaEIndexa() {
        QuestionBank bank = new QuestionBank(null, null, null);
        bank.load(List.of(area("sueno", false, q("sl_horas", "range", null)),
                area("economia", true, q("ec_presupuesto", "options", null, "cero", "bajo"))));
        assertEquals(2, bank.areas().size());
        assertEquals("economia", bank.areaOf("ec_presupuesto"));
        assertEquals("economia_q", bank.questionnaireOf("ec_presupuesto"));
        assertTrue(bank.isSensitive("economia"));
        assertFalse(bank.isSensitive("sueno"));
    }

    @Test
    void idRepetidoDetieneLaCarga() {
        QuestionBank bank = new QuestionBank(null, null, null);
        assertThrows(IllegalStateException.class, () -> bank.load(List.of(area("a", false, q("x", "range", null)),
                area("b", false, q("x", "range", null)))));
    }

    @Test
    void showIfAPreguntaInexistenteDetieneLaCarga() {
        QuestionBank bank = new QuestionBank(null, null, null);
        assertThrows(IllegalStateException.class, () -> bank.load(List.of(area("a", false,
                q("x", "range", Map.of("question", "nada", "in", List.of("si")))))));
    }

    @Test
    void tipoDesconocidoDetieneLaCarga() {
        QuestionBank bank = new QuestionBank(null, null, null);
        assertThrows(IllegalStateException.class, () -> bank.load(List.of(area("a", false, q("x", "slider", null)))));
    }
}
