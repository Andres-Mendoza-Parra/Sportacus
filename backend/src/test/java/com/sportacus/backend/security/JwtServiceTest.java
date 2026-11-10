package com.sportacus.backend.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.sportacus.backend.user.User;

import io.jsonwebtoken.Claims;

class JwtServiceTest {

    private static User user(int tv) {
        User u = User.builder().id(UUID.randomUUID()).email("ana@example.com").passwordHash("x").build();
        u.setTokenVersion(tv);
        return u;
    }

    @Test
    void tokenRevocadoAlSubirLaVersion() {
        JwtService jwt = new JwtService(JwtService.DEV_SECRET, 60, new MockEnvironment());
        User u = user(0);
        Claims c = jwt.parse(jwt.generateToken(u));
        assertTrue(jwt.isTokenValid(c, u));
        assertFalse(jwt.isTokenValid(c, user(1)));
    }

    @Test
    void prodNoArrancaConElSecretoDeDesarrollo() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class, () -> new JwtService(JwtService.DEV_SECRET, 60, prod));
        assertThrows(IllegalStateException.class, () -> new JwtService("", 60, prod));
    }

    @Test
    void secretoCorto() {
        assertThrows(IllegalStateException.class, () -> new JwtService("corto", 60, new MockEnvironment()));
    }
}
