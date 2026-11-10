package com.sportacus.backend.auth;

/** Bloqueo temporal tras 3 fallos seguidos (429 con retryAfterSeconds). */
public class LoginLockedException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginLockedException(long retryAfterSeconds) {
        super(LoginThrottle.LOCKED_MESSAGE);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
