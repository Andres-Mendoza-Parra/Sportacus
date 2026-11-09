package com.sportacus.backend.world.core;

import java.util.List;

/**
 * Cómo debe comportarse el reproductor de sesión en este mundo.
 *
 * @param player     sets (series con registro), interval (temporizador de intervalos), protocol (con chequeos de
 *                   dolor), drills (ejercicios por tiempo/repeticiones sin carga)
 * @param preChecks  chequeos antes de empezar: pain, mood
 * @param postChecks chequeos al terminar: rpe, pain, mood, distance, duration
 * @param logFields  campos a registrar por serie/bloque: reps, load, rir, hold, duration, distance, pain
 * @param tips       consejos breves del nicho que el reproductor muestra en descansos
 */
public record SessionUi(String player, List<String> preChecks, List<String> postChecks, List<String> logFields,
        List<String> tips) {
}
