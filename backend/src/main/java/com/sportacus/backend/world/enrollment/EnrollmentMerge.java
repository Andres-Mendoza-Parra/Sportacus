package com.sportacus.backend.world.enrollment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Modo edición del onboarding de un mundo ya inscrito ("Afina tu plan", auditoría A1): nada de lo que la persona
 * logró se pierde. Reglas puras para poder probarlas.
 */
public final class EnrollmentMerge {

    private EnrollmentMerge() {
    }

    /** Respuestas: las nuevas reemplazan a las anteriores; las que no se reenviaron se conservan. */
    public static Map<String, Object> answers(Map<String, Object> previous, Map<String, Object> fresh) {
        Map<String, Object> out = new LinkedHashMap<>(previous == null ? Map.of() : previous);
        if (fresh != null) fresh.forEach((k, v) -> {
            if (v != null) out.put(k, v);
        });
        return out;
    }

    /**
     * Estado del módulo. Se conserva el estado vivo (cargas, fase, sostenimientos…) salvo las claves que cambian
     * por las respuestas editadas: las que el módulo calcula distinto con las respuestas nuevas
     * ({@code fromFresh}) que con las anteriores ({@code fromPrevious}).
     *
     * @param stored       estado guardado hoy (con lo que avanzó entrenando)
     * @param fromPrevious estado inicial que el módulo produce con las respuestas anteriores
     * @param fromFresh    estado inicial que el módulo produce con las respuestas fusionadas
     */
    public static Map<String, Object> state(Map<String, Object> stored, Map<String, Object> fromPrevious,
            Map<String, Object> fromFresh) {
        Map<String, Object> out = new LinkedHashMap<>(fromFresh == null ? Map.of() : fromFresh);
        if (stored != null) stored.forEach((k, v) -> {
            boolean editedByAnswers = fromFresh != null && fromFresh.containsKey(k)
                    && !Objects.equals(fromFresh.get(k), fromPrevious == null ? null : fromPrevious.get(k));
            if (!editedByAnswers) out.put(k, v);
        });
        return out;
    }

    /** Escalones: el mayor entre el actual y el nuevo; las progresiones que ya tenía se conservan. */
    public static Map<String, Integer> progressions(Map<String, Integer> current, Map<String, Integer> fresh) {
        Map<String, Integer> out = new LinkedHashMap<>(current == null ? Map.of() : current);
        if (fresh != null) fresh.forEach((k, v) -> out.merge(k, v, Math::max));
        return out;
    }
}
