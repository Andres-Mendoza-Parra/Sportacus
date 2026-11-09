package com.sportacus.backend.world.core;

import java.util.List;

/**
 * Reacción de un mundo a un test físico registrado (p. ej. recolocar progresiones o recalcular ritmos).
 *
 * @param effects cambios a aplicar (métricas derivadas, escalones, estado); puede ser null
 */
public record AssessmentOutcome(String classification, List<String> highlights, SessionOutcome effects) {

    public static AssessmentOutcome none() {
        return new AssessmentOutcome(null, List.of(), null);
    }
}
