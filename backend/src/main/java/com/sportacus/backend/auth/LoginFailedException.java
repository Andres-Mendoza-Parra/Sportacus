package com.sportacus.backend.auth;

/** Correo o contraseña incorrectos, con los intentos que quedan antes del bloqueo (401). */
public class LoginFailedException extends RuntimeException {

    private final int remaining;

    public LoginFailedException(int remaining) {
        super(LoginThrottle.failureMessage(remaining));
        this.remaining = remaining;
    }

    public int remaining() {
        return remaining;
    }
}
