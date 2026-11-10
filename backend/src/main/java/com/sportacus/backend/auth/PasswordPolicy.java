package com.sportacus.backend.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Política de contraseñas (auditoría A10): 8 a 72 caracteres (BCrypt solo usa los primeros 72 bytes), al menos una
 * letra y un número, y fuera de una lista corta de contraseñas muy comunes. Reglas puras para poder probarlas.
 */
public final class PasswordPolicy {

    public static final int MIN = 8;
    public static final int MAX = 72;

    private static final Set<String> COMMON = Set.of("12345678", "123456789", "1234567890", "password", "password1",
            "password123", "contraseña", "contrasena1", "qwerty123", "qwertyuiop", "abc12345", "abcd1234", "11111111",
            "00000000", "iloveyou1", "sportacus", "sportacus1", "sportacus123", "colombia1", "colombia123",
            "futbol123", "1q2w3e4r", "a1b2c3d4", "admin123", "welcome1", "bienvenido1", "teamo123");

    private PasswordPolicy() {
    }

    /** Mensaje para la persona si la contraseña no cumple; vacío si cumple. */
    public static Optional<String> problem(String password) {
        if (password == null || password.isEmpty()) {
            return Optional.of("La contraseña es obligatoria");
        }
        if (password.length() < MIN) {
            return Optional.of("La contraseña debe tener al menos " + MIN + " caracteres");
        }
        if (password.length() > MAX || password.getBytes(StandardCharsets.UTF_8).length > MAX) {
            return Optional.of("La contraseña no puede superar " + MAX + " caracteres");
        }
        boolean letter = password.codePoints().anyMatch(Character::isLetter);
        boolean digit = password.codePoints().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            return Optional.of("La contraseña debe tener al menos una letra y un número");
        }
        if (COMMON.contains(password.toLowerCase(Locale.ROOT))) {
            return Optional.of("Esa contraseña es muy común. Elige una más difícil de adivinar");
        }
        return Optional.empty();
    }
}
