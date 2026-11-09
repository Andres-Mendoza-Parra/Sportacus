package com.sportacus.backend.world.core;

import java.util.List;

/** Plan que produce un mundo; el núcleo lo persiste en plan / plan_session / plan_block. */
public record GeneratedPlan(String title, String summary, int weeks, Double score, List<Factor> rationale,
        List<GeneratedSession> sessions) {

    /**
     * @param weekIndex semana (0-based); un plan de 1 semana se repite indefinidamente
     * @param weekday   día sugerido (1=lunes … 7=domingo) o null
     * @param kind      tipo de sesión del nicho: fuerza, tecnica, intervalos, rodaje, protocolo, partido…
     */
    public record GeneratedSession(int weekIndex, int dayIndex, Integer weekday, String title, String focus,
            String kind, Integer estMinutes, String notes, List<GeneratedBlock> blocks) {

        public int estimatedMinutes() {
            if (estMinutes != null) return estMinutes;
            int seconds = blocks.stream().mapToInt(b -> b.prescription().estimatedSeconds() + 30).sum();
            return Math.max(5, Math.round(seconds / 60f));
        }
    }

    /** blockType: calentamiento | principal | tecnica | intervalo | accesorio | vuelta_calma */
    public record GeneratedBlock(String blockType, String movementKey, String title, Prescription prescription,
            String progressionKey, Integer stepIndex, String notes) {

        public static GeneratedBlock of(String blockType, String movementKey, String title, Prescription p) {
            return new GeneratedBlock(blockType, movementKey, title, p, null, null, null);
        }

        public GeneratedBlock withNotes(String value) {
            return new GeneratedBlock(blockType, movementKey, title, prescription, progressionKey, stepIndex, value);
        }

        public GeneratedBlock withProgression(String key, int step) {
            return new GeneratedBlock(blockType, movementKey, title, prescription, key, step, notes);
        }
    }
}
