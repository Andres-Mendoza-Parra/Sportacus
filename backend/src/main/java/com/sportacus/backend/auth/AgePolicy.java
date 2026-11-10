package com.sportacus.backend.auth;

/**
 * Edad mínima y menores de edad (decisión del producto 2026-11-09; Ley 1581 de 2012 art. 7 y Decreto 1377 de 2013
 * art. 12): Sportacus es para personas de 12 años o más; de 12 a 17 se exige la autorización de la madre, el padre o
 * quien tenga la representación legal. La edad se calcula como año actual − año de nacimiento.
 */
public final class AgePolicy {

    public static final int MIN_AGE = 12;
    public static final int ADULT_AGE = 18;
    public static final int MAX_AGE = 110;

    private AgePolicy() {
    }

    public record Result(boolean allowed, boolean minor, String message) {
    }

    public static int age(int birthYear, int currentYear) {
        return currentYear - birthYear;
    }

    public static boolean isMinor(Integer birthYear, int currentYear) {
        return birthYear != null && age(birthYear, currentYear) < ADULT_AGE;
    }

    public static Result check(Integer birthYear, int currentYear, Boolean guardianConsent) {
        if (birthYear == null) {
            return new Result(false, false, "El año de nacimiento es obligatorio");
        }
        int age = age(birthYear, currentYear);
        if (age < 0 || age > MAX_AGE) {
            return new Result(false, false, "El año de nacimiento no es válido");
        }
        if (age < MIN_AGE) {
            return new Result(false, false, "Sportacus es para personas de 12 años o más");
        }
        boolean minor = age < ADULT_AGE;
        if (minor && !Boolean.TRUE.equals(guardianConsent)) {
            return new Result(false, true,
                    "Como tienes menos de 18 años, necesitamos que tu madre, padre o tutor conozca y autorice que uses Sportacus");
        }
        return new Result(true, minor, null);
    }
}
