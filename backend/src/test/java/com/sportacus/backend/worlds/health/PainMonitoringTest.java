package com.sportacus.backend.worlds.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.worlds.health.PainMonitoring.Criteria;
import com.sportacus.backend.worlds.health.PainMonitoring.Decision;
import com.sportacus.backend.worlds.health.PainMonitoring.Input;
import com.sportacus.backend.worlds.health.PainMonitoring.Light;
import com.sportacus.backend.worlds.health.PainMonitoring.State;

class PainMonitoringTest {

    private static final Criteria SIX = new Criteria(3, 3, 6, null);

    private static Input pain(Integer before, Integer after, Integer block) {
        return new Input(before, after, block, null, null, 4);
    }

    @Test
    void clasificaDolorDurante() {
        assertEquals(Light.VERDE, PainMonitoring.classifyDuring(3, 3));
        assertEquals(Light.AMARILLO, PainMonitoring.classifyDuring(4, 3));
        assertEquals(Light.AMARILLO, PainMonitoring.classifyDuring(5, 3));
        assertEquals(Light.ROJO, PainMonitoring.classifyDuring(6, 3));
        assertEquals(Light.SIN_DATOS, PainMonitoring.classifyDuring(null, 3));
    }

    @Test
    void clasificaDolorA24HorasFrenteALaBase() {
        assertEquals(Light.VERDE, PainMonitoring.classifyNextDay(2, 2, 3));
        assertEquals(Light.VERDE, PainMonitoring.classifyNextDay(0, 3, 3));
        assertEquals(Light.VERDE, PainMonitoring.classifyNextDay(5, 4, 3)); // por debajo de su base
        assertEquals(Light.AMARILLO, PainMonitoring.classifyNextDay(1, 4, 3));
        assertEquals(Light.ROJO, PainMonitoring.classifyNextDay(1, 5, 3)); // +4 sobre la base
        assertEquals(Light.ROJO, PainMonitoring.classifyNextDay(4, 6, 3)); // > 5
    }

    @Test
    void verdeSumaYAvanzaAlCumplirLasSesiones() {
        State s = new State(0, 5, 0, 1.0);
        Decision d = PainMonitoring.decide(pain(1, 2, 3), s, SIX, 2, 2, true);
        assertEquals(Light.VERDE, d.light());
        assertTrue(d.advanced());
        assertEquals(1, d.state().phase());
        assertEquals(0, d.state().greenStreak());
        assertEquals(PainMonitoring.VOLUME_NEW_PHASE, d.state().volumeFactor());
    }

    @Test
    void verdeSinAvanceSiFaltanSesiones() {
        Decision d = PainMonitoring.decide(pain(1, 2, null), new State(0, 2, 1, 0.8), SIX, 2, 2, true);
        assertFalse(d.advanced());
        assertEquals(3, d.state().greenStreak());
        assertEquals(0, d.state().redStreak());
        assertEquals(0.9, d.state().volumeFactor(), 1e-9);
    }

    @Test
    void amarilloMantieneFaseReiniciaRachaYBajaVolumen() {
        Decision d = PainMonitoring.decide(pain(2, 4, 5), new State(1, 4, 0, 1.0), SIX, 2, 2, true);
        assertEquals(Light.AMARILLO, d.light());
        assertEquals(1, d.state().phase());
        assertEquals(0, d.state().greenStreak());
        assertEquals(0.8, d.state().volumeFactor(), 1e-9);
    }

    @Test
    void rojoRetrocedeUnaFaseYDosSeguidosRecomiendanConsulta() {
        Decision first = PainMonitoring.decide(pain(2, 7, 7), new State(2, 3, 0, 1.0), SIX, 2, 2, true);
        assertEquals(Light.ROJO, first.light());
        assertTrue(first.regressed());
        assertEquals(1, first.state().phase());
        assertFalse(first.referAdvised());
        Decision second = PainMonitoring.decide(pain(2, 6, null), first.state(), SIX, 2, 2, true);
        assertEquals(0, second.state().phase());
        assertTrue(second.referAdvised());
        assertEquals(PainMonitoring.VOLUME_MIN, second.state().volumeFactor(), 1e-9);
        Decision third = PainMonitoring.decide(pain(2, 8, null), second.state(), SIX, 2, 2, true);
        assertEquals(0, third.state().phase()); // nunca por debajo de la fase 1
        assertFalse(third.regressed());
    }

    @Test
    void dolorA24HorasDeLaSesionAnteriorPuedeVolverRojaUnaSesionTranquila() {
        Input in = new Input(1, 2, 2, 1, 6, 4);
        Decision d = PainMonitoring.decide(in, new State(1, 4, 0, 1.0), SIX, 2, 2, true);
        assertEquals(Light.VERDE, d.duringLight());
        assertEquals(Light.ROJO, d.nextDayLight());
        assertEquals(Light.ROJO, d.light());
        assertEquals(0, d.state().phase());
    }

    @Test
    void postoperatorioSinAvalBloqueaElAvance() {
        Decision d = PainMonitoring.decide(pain(0, 1, 1), new State(0, 5, 0, 1.0), SIX, 2, 0, true);
        assertFalse(d.advanced());
        assertTrue(d.advanceBlocked());
        assertEquals(0, d.state().phase());
    }

    @Test
    void criterioExtraPendienteBloqueaElAvance() {
        Decision d = PainMonitoring.decide(pain(0, 1, 1), new State(1, 8, 0, 1.0), new Criteria(3, 3, 9, null), 2, 2, false);
        assertTrue(d.advanceBlocked());
        assertEquals(1, d.state().phase());
    }

    @Test
    void esfuerzoAltoNoSumaEnLaRutaCardiometabolica() {
        Criteria c = new Criteria(3, 3, 6, 6);
        Decision d = PainMonitoring.decide(new Input(0, 1, null, null, null, 8), new State(0, 2, 0, 1.0), c, 2, 2, true);
        assertEquals(Light.VERDE, d.light());
        assertFalse(d.counted());
        assertEquals(2, d.state().greenStreak());
    }

    @Test
    void sinDatosNoCambiaNada() {
        State s = new State(1, 3, 0, 0.8);
        Decision d = PainMonitoring.decide(pain(null, null, null), s, SIX, 2, 2, true);
        assertEquals(Light.SIN_DATOS, d.light());
        assertEquals(s, d.state());
    }

    @Test
    void ultimaFaseSeMantiene() {
        Criteria maintain = Criteria.from(Map.of("type", "maintain"));
        assertFalse(maintain.canAdvance());
        Decision d = PainMonitoring.decide(pain(0, 1, 1), new State(2, 20, 0, 1.0), maintain, 2, 2, true);
        assertEquals(2, d.state().phase());
        assertFalse(d.advanced());
    }

    @Test
    void dolorA24HorasSeAplicaAlRegistrarlo() {
        State s = new State(1, 4, 0, 1.0);
        Decision green = PainMonitoring.decideNextDay(1, 2, s, 3);
        assertEquals(Light.VERDE, green.light());
        assertEquals(s, green.state()); // no suma de nuevo ni cambia nada
        Decision yellow = PainMonitoring.decideNextDay(1, 4, s, 3);
        assertEquals(Light.AMARILLO, yellow.light());
        assertEquals(1, yellow.state().phase());
        assertEquals(0, yellow.state().greenStreak());
        assertEquals(0.8, yellow.state().volumeFactor(), 1e-9);
        Decision red = PainMonitoring.decideNextDay(1, 6, s, 3);
        assertEquals(Light.ROJO, red.light());
        assertTrue(red.regressed());
        assertEquals(0, red.state().phase());
        assertEquals(1, red.state().redStreak());
        Decision redAgain = PainMonitoring.decideNextDay(0, 7, red.state(), 3);
        assertTrue(redAgain.referAdvised());
        assertEquals(0, redAgain.state().phase());
    }

    @Test
    void criterioDesdeContenidoNuncaEsMasPermisivoQue3() {
        Criteria c = Criteria.from(Map.of("type", "pain", "maxDuring", 5, "maxNextDay", 4, "sessions", 6));
        assertEquals(3, c.maxDuring());
        assertEquals(3, c.maxNextDay());
        assertEquals(6, c.sessions());
    }
}
