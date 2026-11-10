package com.sportacus.backend.auth;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Contador de intentos fallidos por correo (lógica pura; la persistencia está en {@link LoginAttemptRepository}).
 * <ul>
 * <li>3 fallos seguidos → bloqueo de 15 minutos. Durante el bloqueo no se comprueba la contraseña.</li>
 * <li>Un inicio de sesión correcto reinicia el contador.</li>
 * <li>Al terminar el bloqueo, o si el último fallo fue hace más de 15 minutos, el conteo vuelve a empezar.</li>
 * </ul>
 */
public final class LoginThrottle {

    public static final int MAX_FAILURES = 3;
    public static final Duration LOCK = Duration.ofMinutes(15);

    private LoginThrottle() {
    }

    /** Estado guardado. {@code failures} = fallos seguidos desde el último éxito o desbloqueo. */
    public record State(int failures, OffsetDateTime lastFailureAt, OffsetDateTime lockedUntil) {
        public static final State EMPTY = new State(0, null, null);
    }

    /** Segundos que faltan de bloqueo (0 si no está bloqueado). */
    public static long lockedSeconds(State s, OffsetDateTime now) {
        if (s == null || s.lockedUntil() == null || !s.lockedUntil().isAfter(now)) return 0;
        long ms = Duration.between(now, s.lockedUntil()).toMillis();
        return Math.max(1, (ms + 999) / 1000);
    }

    /** Estado tras un fallo. */
    public static State afterFailure(State s, OffsetDateTime now) {
        int previous = 0;
        if (s != null) {
            boolean lockExpired = s.lockedUntil() != null && !s.lockedUntil().isAfter(now);
            boolean stale = s.lastFailureAt() == null || s.lastFailureAt().plus(LOCK).isBefore(now);
            previous = lockExpired || stale ? 0 : s.failures();
        }
        int failures = previous + 1;
        OffsetDateTime lockedUntil = failures >= MAX_FAILURES ? now.plus(LOCK) : null;
        return new State(failures, now, lockedUntil);
    }

    /** Intentos que quedan antes del bloqueo. */
    public static int remaining(State s) {
        return Math.max(0, MAX_FAILURES - (s == null ? 0 : s.failures()));
    }

    public static String failureMessage(int remaining) {
        return "Correo o contraseña incorrectos. Te " + (remaining == 1 ? "queda 1 intento." : "quedan " + remaining + " intentos.");
    }

    public static final String LOCKED_MESSAGE =
            "Demasiados intentos fallidos. Por seguridad, espera 15 minutos para volver a intentarlo.";
}
