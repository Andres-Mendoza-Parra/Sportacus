package com.sportacus.backend.world.core;

/**
 * Prescripción tipada de un bloque de plan (se guarda en {@code plan_block.prescription} como JSONB).
 * Cada mundo usa los campos de su nicho; los nulos no se serializan.
 *
 * <ul>
 * <li>{@code reps}: series × repeticiones (rango repsMin–repsMax), carga sugerida, RIR, tempo, descanso</li>
 * <li>{@code hold}: series × segundos de sostenimiento</li>
 * <li>{@code time}: trabajo continuo durante durationS (rodaje, movilidad), con ritmo/zona</li>
 * <li>{@code interval}: repeats × (workS de trabajo + recoverS de recuperación)</li>
 * <li>{@code distance}: distanceM a un ritmo objetivo</li>
 * </ul>
 *
 * @param intensity texto corto: "suave", "Z2", "RPE 7", "ritmo 5K"…
 * @param paceSecPerKm ritmo objetivo en segundos por km (running)
 * @param painMax dolor máximo tolerado durante el bloque (salud)
 * @param side "ambos", "cada_lado", "izquierdo", "derecho"
 */
public record Prescription(
        String type,
        Integer sets,
        Integer reps,
        Integer repsMin,
        Integer repsMax,
        Double loadKg,
        Integer rir,
        Integer holdS,
        Integer durationS,
        Integer distanceM,
        Integer repeats,
        Integer workS,
        Integer recoverS,
        Integer restS,
        Integer paceSecPerKm,
        String intensity,
        String tempo,
        Integer painMax,
        String side,
        String cue) {

    public static Prescription reps(int sets, int repsMin, int repsMax, int restS) {
        return new Prescription("reps", sets, null, repsMin, repsMax, null, null, null, null, null, null, null, null,
                restS, null, null, null, null, null, null);
    }

    public static Prescription reps(int sets, int reps, int restS) {
        return new Prescription("reps", sets, reps, null, null, null, null, null, null, null, null, null, null, restS,
                null, null, null, null, null, null);
    }

    public static Prescription hold(int sets, int holdS, int restS) {
        return new Prescription("hold", sets, null, null, null, null, null, holdS, null, null, null, null, null, restS,
                null, null, null, null, null, null);
    }

    public static Prescription time(int durationS, String intensity) {
        return new Prescription("time", null, null, null, null, null, null, null, durationS, null, null, null, null,
                null, null, intensity, null, null, null, null);
    }

    public static Prescription interval(int repeats, int workS, int recoverS, String intensity) {
        return new Prescription("interval", null, null, null, null, null, null, null, null, null, repeats, workS,
                recoverS, null, null, intensity, null, null, null, null);
    }

    public static Prescription distance(int distanceM, Integer paceSecPerKm, String intensity) {
        return new Prescription("distance", null, null, null, null, null, null, null, null, distanceM, null, null,
                null, null, paceSecPerKm, intensity, null, null, null, null);
    }

    public Prescription withLoad(Double kg) {
        return new Prescription(type, sets, reps, repsMin, repsMax, kg, rir, holdS, durationS, distanceM, repeats,
                workS, recoverS, restS, paceSecPerKm, intensity, tempo, painMax, side, cue);
    }

    public Prescription withRir(Integer value) {
        return new Prescription(type, sets, reps, repsMin, repsMax, loadKg, value, holdS, durationS, distanceM,
                repeats, workS, recoverS, restS, paceSecPerKm, intensity, tempo, painMax, side, cue);
    }

    public Prescription withPace(Integer secPerKm) {
        return new Prescription(type, sets, reps, repsMin, repsMax, loadKg, rir, holdS, durationS, distanceM, repeats,
                workS, recoverS, restS, secPerKm, intensity, tempo, painMax, side, cue);
    }

    public Prescription withIntensity(String value) {
        return new Prescription(type, sets, reps, repsMin, repsMax, loadKg, rir, holdS, durationS, distanceM, repeats,
                workS, recoverS, restS, paceSecPerKm, value, tempo, painMax, side, cue);
    }

    public Prescription withTempo(String value) {
        return new Prescription(type, sets, reps, repsMin, repsMax, loadKg, rir, holdS, durationS, distanceM, repeats,
                workS, recoverS, restS, paceSecPerKm, intensity, value, painMax, side, cue);
    }

    public Prescription withPainMax(Integer value) {
        return new Prescription(type, sets, reps, repsMin, repsMax, loadKg, rir, holdS, durationS, distanceM, repeats,
                workS, recoverS, restS, paceSecPerKm, intensity, tempo, value, side, cue);
    }

    public Prescription withSide(String value) {
        return new Prescription(type, sets, reps, repsMin, repsMax, loadKg, rir, holdS, durationS, distanceM, repeats,
                workS, recoverS, restS, paceSecPerKm, intensity, tempo, painMax, value, cue);
    }

    public Prescription withCue(String value) {
        return new Prescription(type, sets, reps, repsMin, repsMax, loadKg, rir, holdS, durationS, distanceM, repeats,
                workS, recoverS, restS, paceSecPerKm, intensity, tempo, painMax, side, value);
    }

    public Prescription withSets(Integer value) {
        return new Prescription(type, value, reps, repsMin, repsMax, loadKg, rir, holdS, durationS, distanceM, repeats,
                workS, recoverS, restS, paceSecPerKm, intensity, tempo, painMax, side, cue);
    }

    public Prescription withRest(Integer value) {
        return new Prescription(type, sets, reps, repsMin, repsMax, loadKg, rir, holdS, durationS, distanceM, repeats,
                workS, recoverS, value, paceSecPerKm, intensity, tempo, painMax, side, cue);
    }

    /** Duración estimada del bloque en segundos (para calcular los minutos de la sesión). */
    public int estimatedSeconds() {
        int s = sets == null ? 1 : sets;
        int rest = restS == null ? 0 : restS;
        return switch (type) {
            case "reps" -> {
                int r = reps != null ? reps : repsMax != null ? repsMax : 10;
                yield s * (r * 3) + Math.max(0, s - 1) * rest;
            }
            case "hold" -> s * (holdS == null ? 30 : holdS) + Math.max(0, s - 1) * rest;
            case "time" -> durationS == null ? 300 : durationS;
            case "interval" -> (repeats == null ? 1 : repeats) * ((workS == null ? 60 : workS) + (recoverS == null ? 0 : recoverS));
            case "distance" -> distanceM == null ? 600
                    : (int) (distanceM / 1000.0 * (paceSecPerKm == null ? 420 : paceSecPerKm));
            default -> 120;
        };
    }
}
