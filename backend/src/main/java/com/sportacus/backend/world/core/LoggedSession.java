package com.sportacus.backend.world.core;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Sesión registrada por el usuario, con lo que estaba planificado al lado para comparar. */
public record LoggedSession(UUID id, UUID planSessionId, String sessionKind, LocalDate performedOn,
        Integer durationMin, Integer rpe, Integer painBefore, Integer painAfter, Integer moodBefore, Integer moodAfter,
        Double distanceM, String notes, List<LoggedBlock> blocks) {

    /** Carga interna de la sesión (sRPE de Foster): RPE × minutos. */
    public Double sessionLoad() {
        return rpe == null || durationMin == null ? null : (double) rpe * durationMin;
    }

    public record LoggedBlock(UUID planBlockId, String movementKey, int setIndex, Integer reps, Double loadKg,
            Integer rir, Integer holdS, Integer durationS, Double distanceM, Integer pain, boolean completed,
            String blockType, Prescription planned, String progressionKey, Integer stepIndex) {
    }
}
