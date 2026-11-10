package com.sportacus.backend.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.core.ProfileFacts;
import com.sportacus.backend.world.core.ProfileInsights;

class InsightRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    private static ProfileFacts facts(Map<String, Object> values, Map<String, LocalDate> dates) {
        return new ProfileFacts(new Answers(values), dates);
    }

    private static HealthSummary screened(List<String> flags) {
        return new HealthSummary(true, "bajo", flags, false, false, false, Set.of(), List.of(), null);
    }

    @Test
    void sinDatosTodoNeutro() {
        ProfileInsights in = InsightRules.derive(ProfileFacts.empty(), HealthSummary.unknown(), TODAY);
        assertNull(in.readiness());
        assertNull(in.altitudeM());
        assertFalse(in.eatingCaution());
        assertEquals(1.0, in.volumeFactor());
        assertEquals(1.0, in.altitudePaceFactor());
    }

    @Test
    void checkinDeHoyBajaElVolumenYExplicaPorQue() {
        Map<String, Object> v = Map.of("ck_sueno_horas", 4, "ck_energia", 2, "ck_estres", 3, "ck_dolor", 0);
        Map<String, LocalDate> d = Map.of("ck_sueno_horas", TODAY, "ck_energia", TODAY, "ck_estres", TODAY,
                "ck_dolor", TODAY);
        ProfileInsights in = InsightRules.derive(facts(v, d), HealthSummary.unknown(), TODAY);
        assertNotNull(in.readiness());
        assertEquals(0.7, in.readiness().factor(), 0.001);
        assertTrue(in.readiness().reasons().contains("Dormiste 4 h"));
    }

    @Test
    void checkinDeAyerNoCuenta() {
        Map<String, Object> v = Map.of("ck_sueno_horas", 4, "ck_energia", 1);
        Map<String, LocalDate> d = Map.of("ck_sueno_horas", TODAY.minusDays(1), "ck_energia", TODAY.minusDays(1));
        assertNull(InsightRules.derive(facts(v, d), HealthSummary.unknown(), TODAY).readiness());
    }

    @Test
    void dolorFuerteSugiereDescansoActivoYNuncaBajaDe60() {
        Map<String, Object> v = Map.of("ck_sueno_horas", 3, "ck_sueno_calidad", 1, "ck_energia", 1, "ck_estres", 5,
                "ck_dolor", 8);
        Map<String, LocalDate> d = new HashMap<>();
        v.keySet().forEach(k -> d.put(k, TODAY));
        var r = InsightRules.derive(facts(v, d), HealthSummary.unknown(), TODAY).readiness();
        assertEquals(0.6, r.factor(), 0.001);
        assertTrue(r.label().startsWith("Hoy, descanso activo"));
    }

    @Test
    void who5SoloConLosCincoItems() {
        Map<String, Object> v = new HashMap<>(Map.of("bi_who5_1", "3", "bi_who5_2", "3", "bi_who5_3", "2",
                "bi_who5_4", "4"));
        assertNull(InsightRules.who5(facts(v, Map.of())));
        v.put("bi_who5_5", "5");
        assertEquals(68, InsightRules.who5(facts(v, Map.of())));
    }

    @Test
    void medicamentosRelacionConLaComidaYEntorno() {
        Map<String, Object> v = Map.of("rc_preocupacion", "a_menudo", "ub_altitud", "2000_3000",
                "tr_transporte", "bici", "tr_transporte_min", 20, "so_mascota", List.of("perro"));
        ProfileInsights in = InsightRules.derive(facts(v, Map.of()),
                screened(List.of("fc_no_fiable", "anticoagulado")), TODAY);
        assertTrue(in.eatingCaution());
        assertTrue(in.heartRateUnreliable());
        assertTrue(in.avoidContact());
        assertTrue(in.hasDog());
        assertEquals(2500, in.altitudeM());
        assertEquals(1.04, in.altitudePaceFactor());
        assertEquals(200, in.activeCommuteMinWeek());
    }
}
