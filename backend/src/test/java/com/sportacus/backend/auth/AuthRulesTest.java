package com.sportacus.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class AuthRulesTest {

    // ── Edad ──────────────────────────────────────────────────

    @Test
    void menoresDe12NoPueden() {
        AgePolicy.Result r = AgePolicy.check(2015, 2026, true); // 11 años
        assertFalse(r.allowed());
        assertEquals("Sportacus es para personas de 12 años o más", r.message());
    }

    @Test
    void de12a17NecesitanAutorizacion() {
        assertFalse(AgePolicy.check(2014, 2026, null).allowed()); // 12 años
        assertFalse(AgePolicy.check(2009, 2026, false).allowed()); // 17 años
        AgePolicy.Result ok = AgePolicy.check(2014, 2026, true);
        assertTrue(ok.allowed());
        assertTrue(ok.minor());
    }

    @Test
    void adultosNoNecesitanAutorizacion() {
        AgePolicy.Result r = AgePolicy.check(2008, 2026, null); // 18 años
        assertTrue(r.allowed());
        assertFalse(r.minor());
        assertFalse(AgePolicy.isMinor(2008, 2026));
        assertTrue(AgePolicy.isMinor(2009, 2026));
        assertFalse(AgePolicy.isMinor(null, 2026));
    }

    @Test
    void anioInvalidoOAusente() {
        assertFalse(AgePolicy.check(null, 2026, true).allowed());
        assertFalse(AgePolicy.check(2030, 2026, true).allowed());
        assertFalse(AgePolicy.check(1900, 2026, true).allowed());
    }

    // ── Contraseña ────────────────────────────────────────────

    @Test
    void politicaDeContrasena() {
        assertTrue(PasswordPolicy.problem("corto1").isPresent());
        assertTrue(PasswordPolicy.problem("soloLetrasAqui").isPresent());
        assertTrue(PasswordPolicy.problem("1234567890123").isPresent());
        assertTrue(PasswordPolicy.problem("Password1").isPresent()); // común
        assertTrue(PasswordPolicy.problem("a1" + "x".repeat(71)).isPresent()); // 73 caracteres
        assertTrue(PasswordPolicy.problem("ñ1" + "ñ".repeat(40)).isPresent()); // > 72 bytes en UTF-8
        assertTrue(PasswordPolicy.problem(null).isPresent());
        assertFalse(PasswordPolicy.problem("trotar2026").isPresent());
        assertFalse(PasswordPolicy.problem("a1" + "x".repeat(70)).isPresent()); // 72 exactos
    }

    // ── Bloqueo de inicio de sesión ───────────────────────────

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 9, 10, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void tresFallosBloquean15Minutos() {
        LoginThrottle.State s = LoginThrottle.afterFailure(LoginThrottle.State.EMPTY, T0);
        assertEquals(2, LoginThrottle.remaining(s));
        assertNull(s.lockedUntil());
        s = LoginThrottle.afterFailure(s, T0.plusSeconds(10));
        assertEquals(1, LoginThrottle.remaining(s));
        assertNull(s.lockedUntil());
        s = LoginThrottle.afterFailure(s, T0.plusSeconds(20));
        assertNotNull(s.lockedUntil());
        assertEquals(15 * 60, LoginThrottle.lockedSeconds(s, T0.plusSeconds(20)));
        assertEquals(60, LoginThrottle.lockedSeconds(s, T0.plusSeconds(20 + 14 * 60)));
        assertEquals(0, LoginThrottle.lockedSeconds(s, T0.plusSeconds(20 + 15 * 60)));
    }

    @Test
    void trasElBloqueoElConteoEmpiezaDeNuevo() {
        LoginThrottle.State s = new LoginThrottle.State(3, T0, T0.plusMinutes(15));
        LoginThrottle.State after = LoginThrottle.afterFailure(s, T0.plusMinutes(16));
        assertEquals(1, after.failures());
        assertNull(after.lockedUntil());
    }

    @Test
    void fallosViejosNoCuentan() {
        LoginThrottle.State s = new LoginThrottle.State(2, T0, null);
        assertEquals(1, LoginThrottle.afterFailure(s, T0.plusMinutes(20)).failures());
        assertEquals(3, LoginThrottle.afterFailure(s, T0.plusMinutes(5)).failures());
    }

    @Test
    void mensajes() {
        assertEquals("Correo o contraseña incorrectos. Te quedan 2 intentos.", LoginThrottle.failureMessage(2));
        assertEquals("Correo o contraseña incorrectos. Te queda 1 intento.", LoginThrottle.failureMessage(1));
        assertEquals(0, LoginThrottle.lockedSeconds(LoginThrottle.State.EMPTY, T0));
    }
}
