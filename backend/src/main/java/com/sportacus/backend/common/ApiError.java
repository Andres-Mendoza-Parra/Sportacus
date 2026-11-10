package com.sportacus.backend.common;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Error de la API. {@code retryAfterSeconds} solo viene en respuestas 429 (bloqueo temporal del inicio de sesión);
 * en el resto es null y no se serializa.
 */
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        List<String> messages,
        Long retryAfterSeconds
) {
    public static ApiError of(int status, String error, List<String> messages) {
        return new ApiError(OffsetDateTime.now(), status, error, messages, null);
    }

    public static ApiError retryAfter(int status, String error, List<String> messages, long retryAfterSeconds) {
        return new ApiError(OffsetDateTime.now(), status, error, messages, retryAfterSeconds);
    }
}
