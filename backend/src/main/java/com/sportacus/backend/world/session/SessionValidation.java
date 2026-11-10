package com.sportacus.backend.world.session;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.world.session.SessionService.BlockLogRequest;
import com.sportacus.backend.world.session.SessionService.SessionLogRequest;

/**
 * Rangos de un registro de sesión (auditoría: fechas futuras o de 1900 inflaban la racha, cargas negativas, ánimo 999
 * daba 500). Todo fuera de rango es un 400 con un mensaje claro. Reglas puras.
 */
public final class SessionValidation {

    public static final int MAX_DAYS_BACK = 60;
    public static final int TITLE_MAX = 120;
    public static final int NOTES_MAX = 2000;
    public static final int MAX_BLOCKS = 300;

    private SessionValidation() {
    }

    /** @param today hoy en Bogotá */
    public static void validate(SessionLogRequest r, LocalDate today) {
        if (r == null) bad("Faltan los datos de la sesión");
        if (r.performedOn() != null) {
            if (r.performedOn().isAfter(today)) bad("La fecha de la sesión no puede ser futura");
            if (r.performedOn().isBefore(today.minusDays(MAX_DAYS_BACK))) {
                bad("Solo puedes registrar sesiones de los últimos " + MAX_DAYS_BACK + " días");
            }
        }
        range(r.durationMin(), 1, 600, "La duración va de 1 a 600 minutos");
        range(r.rpe(), 1, 10, "El esfuerzo percibido va de 1 a 10");
        range(r.painBefore(), 0, 10, "El dolor va de 0 a 10");
        range(r.painAfter(), 0, 10, "El dolor va de 0 a 10");
        range(r.moodBefore(), 1, 5, "El ánimo va de 1 a 5");
        range(r.moodAfter(), 1, 5, "El ánimo va de 1 a 5");
        range(r.distanceM(), 0, 200_000, "La distancia va de 0 a 200 km");
        if (r.title() != null && r.title().length() > TITLE_MAX) bad("El título admite hasta " + TITLE_MAX + " caracteres");
        if (r.notes() != null && r.notes().length() > NOTES_MAX) bad("Las notas admiten hasta " + NOTES_MAX + " caracteres");
        if (r.blocks() == null) return;
        if (r.blocks().size() > MAX_BLOCKS) bad("Demasiados registros en una sesión");
        for (BlockLogRequest b : r.blocks()) {
            if (b == null) bad("Hay un registro de ejercicio vacío");
            range(b.setIndex(), 0, 100, "Serie no válida");
            range(b.reps(), 0, 1000, "Las repeticiones van de 0 a 1000");
            range(b.loadKg(), 0, 1000, "La carga va de 0 a 1000 kg");
            range(b.rir(), 0, 10, "Las repeticiones en reserva van de 0 a 10");
            range(b.holdS(), 0, 36_000, "El tiempo sostenido no es válido");
            range(b.durationS(), 0, 36_000, "La duración del ejercicio no es válida");
            range(b.distanceM(), 0, 200_000, "La distancia va de 0 a 200 km");
            range(b.pain(), 0, 10, "El dolor va de 0 a 10");
            if (b.movementKey() != null && b.movementKey().length() > 60) bad("Ejercicio no válido");
        }
    }

    private static void range(Integer v, int min, int max, String msg) {
        if (v != null && (v < min || v > max)) bad(msg);
    }

    private static void range(Double v, double min, double max, String msg) {
        if (v != null && (v.isNaN() || v < min || v > max)) bad(msg);
    }

    private static void bad(String msg) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
