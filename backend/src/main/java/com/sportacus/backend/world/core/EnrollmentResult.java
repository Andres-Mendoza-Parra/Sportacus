package com.sportacus.backend.world.core;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Resultado de procesar el onboarding de un mundo.
 *
 * @param profile      respuestas normalizadas que se guardan en user_world.profile
 * @param state        estado inicial del módulo
 * @param progressions escalón inicial por progresión (clave → índice)
 * @param rationale    por qué se eligió esa ruta y nivel
 * @param notices      avisos para el usuario (seguridad, recomendaciones)
 */
public record EnrollmentResult(String trackKey, int level, Map<String, Object> profile, Map<String, Object> state,
        List<GoalSpec> goals, Map<String, Integer> progressions, List<Factor> rationale, List<String> notices) {

    public record GoalSpec(String goalKey, String label, Map<String, Object> target, LocalDate targetDate) {
    }
}
