package com.sportacus.backend.auth;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.sportacus.backend.common.ApiError;

/** Errores propios del inicio de sesión y el registro. Va antes del manejador global. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    @ExceptionHandler(LoginFailedException.class)
    public ResponseEntity<ApiError> failed(LoginFailedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "No autorizado", List.of(ex.getMessage())));
    }

    @ExceptionHandler(LoginLockedException.class)
    public ResponseEntity<ApiError> locked(LoginLockedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(ApiError.retryAfter(429, "Demasiados intentos", List.of(ex.getMessage()),
                        ex.retryAfterSeconds()));
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiError> emailTaken(EmailAlreadyRegisteredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "No se pudo crear la cuenta", List.of(EmailAlreadyRegisteredException.MESSAGE)));
    }
}
