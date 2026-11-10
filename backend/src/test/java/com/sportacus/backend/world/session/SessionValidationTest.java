package com.sportacus.backend.world.session;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.world.session.SessionService.BlockLogRequest;
import com.sportacus.backend.world.session.SessionService.SessionLogRequest;

class SessionValidationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    private static SessionLogRequest req(LocalDate day, Integer dur, Integer rpe, Integer mood, Double dist, String notes,
            List<BlockLogRequest> blocks) {
        return new SessionLogRequest(null, "Sesión", day, dur, rpe, 0, 2, mood, mood, dist, notes, blocks, null);
    }

    private static BlockLogRequest block(Integer reps, Double load, Double dist) {
        return new BlockLogRequest(null, "sentadilla", 0, reps, load, 2, null, null, dist, null, true);
    }

    private static void assertBad(SessionLogRequest r) {
        var e = assertThrows(ResponseStatusException.class, () -> SessionValidation.validate(r, TODAY));
        assertEquals(400, e.getStatusCode().value());
    }

    @Test
    void registroNormalPasa() {
        assertDoesNotThrow(() -> SessionValidation.validate(
                req(TODAY, 45, 7, 4, 5000.0, "bien", List.of(block(10, 60.0, null))), TODAY));
        assertDoesNotThrow(() -> SessionValidation.validate(req(null, null, null, null, null, null, null), TODAY));
    }

    @Test
    void fechasFuturasOAntiguasSeRechazan() {
        assertBad(req(TODAY.plusDays(1), 30, 5, 3, null, null, null));
        assertBad(req(LocalDate.of(1900, 1, 1), 30, 5, 3, null, null, null));
        assertBad(req(TODAY.minusDays(61), 30, 5, 3, null, null, null));
        assertDoesNotThrow(() -> SessionValidation.validate(req(TODAY.minusDays(60), 30, 5, 3, null, null, null), TODAY));
    }

    @Test
    void rangosDeLaSesion() {
        assertBad(req(TODAY, 0, 5, 3, null, null, null));
        assertBad(req(TODAY, 601, 5, 3, null, null, null));
        assertBad(req(TODAY, 30, 11, 3, null, null, null));
        assertBad(req(TODAY, 30, 5, 999, null, null, null));
        assertBad(req(TODAY, 30, 5, 0, null, null, null));
        assertBad(req(TODAY, 30, 5, 3, -1.0, null, null));
        assertBad(req(TODAY, 30, 5, 3, 200_001.0, null, null));
        assertBad(req(TODAY, 30, 5, 3, null, "x".repeat(2001), null));
        assertBad(new SessionLogRequest(null, "t".repeat(121), TODAY, 30, 5, 0, 0, 3, 3, null, null, null, null));
        assertBad(new SessionLogRequest(null, null, TODAY, 30, 5, 11, 0, 3, 3, null, null, null, null));
    }

    @Test
    void rangosDeLasSeries() {
        assertBad(req(TODAY, 30, 5, 3, null, null, List.of(block(-1, 20.0, null))));
        assertBad(req(TODAY, 30, 5, 3, null, null, List.of(block(1001, 20.0, null))));
        assertBad(req(TODAY, 30, 5, 3, null, null, List.of(block(10, -5.0, null))));
        assertBad(req(TODAY, 30, 5, 3, null, null, List.of(block(10, 1000.5, null))));
        assertBad(req(TODAY, 30, 5, 3, null, null, List.of(block(10, 20.0, 300_000.0))));
        assertDoesNotThrow(() -> SessionValidation.validate(
                req(TODAY, 30, 5, 3, null, null, List.of(block(0, 0.0, 0.0), block(1000, 1000.0, 200_000.0))), TODAY));
    }
}
