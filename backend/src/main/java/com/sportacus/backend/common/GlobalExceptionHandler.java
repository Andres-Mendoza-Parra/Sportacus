package com.sportacus.backend.common;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.auth.EmailAlreadyRegisteredException;
import com.sportacus.backend.auth.InvalidCredentialsException;
import com.sportacus.backend.rag.RagUnavailableException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RagUnavailableException.class)
    public ResponseEntity<ApiError> handleRagUnavailable(RagUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(503, "Asistente no disponible",
                        List.of("El motor de IA local no está disponible en este momento. Intenta de nuevo en un momento.")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> messages = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(ApiError.of(400, "Datos inválidos", messages));
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Correo ya registrado", List.of(ex.getMessage())));
    }

    @ExceptionHandler({InvalidCredentialsException.class, BadCredentialsException.class})
    public ResponseEntity<ApiError> handleInvalidCredentials(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "No autorizado", List.of("Correo o contraseña incorrectos")));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return ResponseEntity.status(status).body(ApiError.of(status.value(), status.getReasonPhrase(), List.of(message)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Datos inválidos", List.of("El cuerpo de la petición no es válido")));
    }

    /** Registro repetido (p. ej. una carrera entre dos peticiones iguales). */
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ResponseEntity<ApiError> handleDuplicate(org.springframework.dao.DuplicateKeyException ex) {
        log.warn("Registro duplicado rechazado ({})", rootName(ex));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(409, "Conflicto",
                List.of("Eso ya estaba registrado. Recarga para ver la información más reciente.")));
    }

    /**
     * Restricciones de la base de datos (rangos, claves foráneas…): 400 con mensaje neutro. Nunca se registra el
     * mensaje original porque incluye la fila completa, que puede llevar datos de salud.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        log.warn("Datos rechazados por una restricción de la base de datos ({})", rootName(ex));
        return ResponseEntity.badRequest().body(ApiError.of(400, "Datos inválidos",
                List.of("Algunos datos no son válidos. Revisa los valores e intenta de nuevo.")));
    }

    /** Parámetros con tipo inválido (p. ej. un UUID mal formado en la ruta) o ausentes. */
    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleBadParam(Exception ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Datos inválidos", List.of("Algún parámetro de la petición no es válido")));
    }

    /** Ruta inexistente: 404, no un 500 con traza en el log. */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "No encontrado", List.of("No encontramos lo que buscas")));
    }

    private static String rootName(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String state = t instanceof java.sql.SQLException sql ? " " + sql.getSQLState() : "";
        return t.getClass().getSimpleName() + state;
    }

    /** Errores no previstos: se registran completos en el log y al cliente se le da un mensaje neutro. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Error no controlado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "Error interno", List.of("Ocurrió un error inesperado. Intenta de nuevo.")));
    }
}
