package com.sportacus.backend.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.dashboard.DashboardQueryRepository.ExerciseLoad;
import com.sportacus.backend.dashboard.DashboardRules.InsightInput;
import com.sportacus.backend.dashboard.dto.DashboardParts.InsightResponse;

/** Reglas puras del dashboard: selección del gráfico, consejos, score e indicadores. */
class DashboardRulesTest {

    // ── Selección del gráfico según la meta ──

    @Test
    void metaMuscularUsaRadarSiLaRutinaTieneFuerza() {
        assertEquals("radar", DashboardRules.focusType("ganancia_muscular", 5));
        assertEquals("radar", DashboardRules.focusType("rendimiento_deportivo", 2));
    }

    @Test
    void metaMuscularConRutinaSinFuerzaCaeAComposicion() {
        assertEquals("donut", DashboardRules.focusType("ganancia_muscular", 0));
        assertEquals("donut", DashboardRules.focusType("ganancia_muscular", 1));
    }

    @Test
    void perdidaDeGrasaUsaDonut() {
        assertEquals("donut", DashboardRules.focusType("perdida_grasa", 6));
    }

    @Test
    void metasDeBienestarUsanRadar() {
        for (String goal : List.of("bienestar_general", "flexibilidad", "gestion_estres", "rehabilitacion")) {
            assertEquals("radar", DashboardRules.focusType(goal, 0), goal);
        }
    }

    @Test
    void habitoActivoUsaBarras() {
        assertEquals("bars", DashboardRules.focusType("habito_activo", 4));
    }

    @Test
    void sinMetaUsaMedidor() {
        assertEquals("gauge", DashboardRules.focusType(null, 6));
        assertEquals("gauge", DashboardRules.focusType("meta_desconocida", 6));
    }

    @Test
    void todoTipoDevueltoEsValido() {
        for (String goal : new String[] { null, "ganancia_muscular", "perdida_grasa", "bienestar_general",
                "habito_activo", "otra" }) {
            assertTrue(DashboardRules.FOCUS_TYPES.contains(DashboardRules.focusType(goal, 3)));
        }
    }

    // ── Score ──

    @Test
    void scoreEsPromedioPonderado() {
        assertEquals(0, DashboardRules.globalScore(0, 0, 0));
        assertEquals(100, DashboardRules.globalScore(100, 100, 100));
        // 50*0.4 + 80*0.4 + 0*0.2 = 52
        assertEquals(52, DashboardRules.globalScore(50, 80, 0));
    }

    // ── Insights ──

    private InsightInput input(boolean injury, Double sleep, Integer stress, Double water, Double bmi, int strength,
            boolean route, int done, int total) {
        return new InsightInput(injury, sleep, stress, water, bmi, strength, route, done, total);
    }

    private List<String> titles(List<InsightResponse> list) {
        return list.stream().map(InsightResponse::title).toList();
    }

    @Test
    void perfilVacioSugiereCompletarPerfilYEmpezarRutina() {
        List<InsightResponse> r = DashboardRules.insights(input(false, null, null, null, null, 0, true, 0, 6));
        assertTrue(titles(r).contains("Afina tu perfil"));
        assertTrue(titles(r).contains("Tu rutina te espera"));
        assertTrue(r.stream().anyMatch(i -> "/questionnaires".equals(i.route())));
        assertTrue(r.stream().anyMatch(i -> "/routine".equals(i.route())));
    }

    @Test
    void suenoBajoEstresAltoYAguaBajaGeneranAlertas() {
        List<InsightResponse> r = DashboardRules.insights(input(false, 5.0, 5, 1.0, null, 80, false, 0, 0));
        assertTrue(titles(r).containsAll(List.of("Duermes poco", "Estrés alto", "Hidratación baja")));
        assertTrue(r.stream().allMatch(i -> "warn".equals(i.tone())));
    }

    @Test
    void lesionActivaTienePrioridadYNuncaSeSuperanCuatro() {
        List<InsightResponse> r = DashboardRules.insights(input(true, 5.0, 5, 1.0, 35.0, 10, true, 0, 6));
        assertEquals("Lesión activa registrada", r.get(0).title());
        assertEquals(4, r.size());
    }

    @Test
    void sinAlertasSeReconoceElBuenEstado() {
        List<InsightResponse> r = DashboardRules.insights(input(false, 8.0, 2, 2.5, 22.0, 90, false, 0, 0));
        assertEquals(1, r.size());
        assertEquals("good", r.get(0).tone());
    }

    @Test
    void checklistCompletoYParcial() {
        assertTrue(titles(DashboardRules.insights(input(false, 8.0, 2, 2.5, null, 90, true, 6, 6)))
                .contains("¡Rutina completada!"));
        assertTrue(titles(DashboardRules.insights(input(false, 8.0, 2, 2.5, null, 90, true, 2, 6)))
                .contains("Buen ritmo"));
    }

    @Test
    void datosDesconocidosNoActivanReglas() {
        List<InsightResponse> r = DashboardRules.insights(input(false, null, null, null, null, 100, false, 0, 0));
        assertEquals(List.of("Todo en orden"), titles(r));
    }

    // ── Indicadores y puntajes ──

    @Test
    void imcClasificaYNoInventa() {
        assertNull(DashboardRules.bmiMetric(null));
        assertEquals("good", DashboardRules.bmiMetric(22.0).status());
        assertEquals("low", DashboardRules.bmiMetric(17.0).status());
        assertEquals("warn", DashboardRules.bmiMetric(27.0).status());
        assertEquals("22.0", DashboardRules.bmiMetric(22.0).value());
    }

    @Test
    void indicadoresAusentesSonNull() {
        assertNull(DashboardRules.sleepMetric(null));
        assertNull(DashboardRules.waterMetric(null));
        assertNull(DashboardRules.daysMetric(null));
        assertNull(DashboardRules.sessionTimeMetric(null));
        assertNull(DashboardRules.sessionTimeMetric("raro"));
    }

    @Test
    void puntajesDeBienestar() {
        assertNull(DashboardRules.sleepScore(null, null));
        assertEquals(100, DashboardRules.sleepScore(8.0, 5));
        assertEquals(23, DashboardRules.sleepScore(4.0, 1)); // (25 + 20) / 2 = 22.5 -> 23
        assertEquals(100, DashboardRules.calmScore(1));
        assertEquals(0, DashboardRules.calmScore(5));
        assertNull(DashboardRules.calmScore(null));
        assertEquals(100, DashboardRules.hydrationScore(3.0));
        assertEquals(50, DashboardRules.hydrationScore(1.25));
        assertEquals(100, DashboardRules.nutritionScore(4));
        assertNull(DashboardRules.nutritionScore(null));
        assertEquals(60, DashboardRules.dailyActivityScore(null, "moderado"));
        assertEquals(50, DashboardRules.dailyActivityScore(4000, "sedentario"));
        assertNull(DashboardRules.dailyActivityScore(null, null));
    }

    // ── Estimaciones del servicio ──

    @Test
    void minutosEstimadosYCategorias() {
        // 3 series x 10 reps (30 s) + 2 descansos de 60 s = 210 s = 3.5 min
        assertEquals(3.5, DashboardService.estimateMinutes(new ExerciseLoad(null, 3, 10, null, 60, "Pecho", "Fuerza")), 1e-9);
        // 2 series x 30 s fijos + 1 descanso de 30 s = 90 s
        assertEquals(1.5, DashboardService.estimateMinutes(new ExerciseLoad(null, 2, null, 30, 30, "Core", "Flexibilidad")), 1e-9);
        assertEquals("cardio", DashboardService.kindOf("Cardiovascular"));
        assertEquals("movilidad", DashboardService.kindOf("Mente-Cuerpo"));
        assertEquals("fuerza", DashboardService.kindOf("Fuerza"));
    }
}
