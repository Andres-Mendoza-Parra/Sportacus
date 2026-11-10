package com.sportacus.backend.world.plan;

import java.util.ArrayList;
import java.util.List;

import com.sportacus.backend.world.core.Prescription;

/**
 * Explica una prescripción en lenguaje claro ("4 series de 8 a 12 repeticiones", "RIR 2: …", "Tempo 2-1-2: …").
 * Reglas puras: sin dependencias, probadas en {@code DoseTextTest}.
 */
public final class DoseText {

    private DoseText() {
    }

    /**
     * @param readiness factor de volumen de hoy (1 = normal; null si no hay check-in)
     */
    public static List<String> explain(Prescription p, Double readiness) {
        List<String> out = new ArrayList<>();
        if (p == null || p.type() == null) return out;
        String side = "cada_lado".equals(p.side()) ? " por cada lado" : "";
        switch (p.type()) {
            case "reps" -> {
                String reps = p.reps() != null ? p.reps() + " repeticiones"
                        : p.repsMin() != null && p.repsMax() != null
                                ? "entre " + p.repsMin() + " y " + p.repsMax() + " repeticiones" : "repeticiones";
                out.add(sets(p.sets()) + reps + side + ".");
                if (p.repsMin() != null && p.repsMax() != null && !p.repsMin().equals(p.repsMax())) {
                    out.add("Cuando completes " + p.repsMax() + " en todas las series con buena técnica, toca subir "
                            + "la dificultad o la carga.");
                }
            }
            case "hold" -> out.add((p.sets() == null ? "Sostén" : p.sets() == 1 ? "1 serie: sostén" : p.sets() + " series: sostén")
                    + " la posición " + seconds(p.holdS()) + side + ".");
            case "time" -> out.add("Durante " + duration(p.durationS()) + side + ".");
            case "interval" -> {
                out.add((p.repeats() == null ? "Repite " : p.repeats() + " repeticiones: ") + duration(p.workS())
                        + " de trabajo y " + duration(p.recoverS()) + " de recuperación.");
            }
            case "distance" -> {
                String d = p.distanceM() == null ? "la distancia indicada"
                        : p.distanceM() >= 1000 ? fmtKm(p.distanceM()) + " km" : p.distanceM() + " m";
                out.add("Recorre " + d + ".");
            }
            default -> {
                // tipo desconocido: solo lo que sigue
            }
        }
        if (p.loadKg() != null && p.loadKg() > 0) {
            out.add("Carga orientativa: " + fmt(p.loadKg()) + " kg. Si no completas las repeticiones con buena técnica, baja el peso.");
        }
        if (p.rir() != null) {
            out.add(p.rir() == 0 ? "Hasta el fallo técnico: para cuando ya no puedas hacer otra con buena técnica."
                    : "RIR " + p.rir() + ": termina cada serie sintiendo que podrías hacer " + p.rir()
                            + (p.rir() == 1 ? " repetición más" : " repeticiones más") + " con buena técnica.");
        }
        if (p.tempo() != null && p.tempo().matches("\\d+-\\d+-\\d+")) {
            String[] t = p.tempo().split("-");
            out.add("Ritmo " + p.tempo() + ": baja en " + t[0] + " s, pausa " + t[1] + " s y sube en " + t[2]
                    + " s. Moverte lento da control y protege las articulaciones.");
        }
        if (p.paceSecPerKm() != null) {
            out.add("Ritmo objetivo: " + pace(p.paceSecPerKm()) + " por kilómetro (orientativo; manda la sensación).");
        }
        if (p.intensity() != null && !p.intensity().isBlank()) {
            out.add("Intensidad: " + p.intensity() + ".");
        }
        if (p.restS() != null && p.restS() > 0 && (p.sets() == null || p.sets() > 1)) {
            out.add("Descansa " + duration(p.restS()) + " entre series.");
        }
        if (p.painMax() != null) {
            out.add("Límite de dolor: " + p.painMax() + "/10. Una molestia leve está bien; si pasa de "
                    + p.painMax() + ", para o haz una versión más suave.");
        }
        if (p.cue() != null && !p.cue().isBlank()) out.add("Clave: " + p.cue());
        if (readiness != null && readiness < 0.95) {
            int pct = (int) Math.round((1 - readiness) * 100);
            if (p.sets() != null && p.sets() > 1) {
                int today = Math.max(1, (int) Math.round(p.sets() * readiness));
                out.add("Hoy, según tu check-in: haz " + today + (today == 1 ? " serie" : " series")
                        + " en vez de " + p.sets() + " (−" + pct + " %) y quédate lejos del fallo.");
            } else {
                out.add("Hoy, según tu check-in: baja la intensidad o la duración un " + pct + " %.");
            }
        }
        return out;
    }

    /** Resumen corto de la dosis ("3 × 8–12", "4 × 30 s", "20 min"). */
    public static String shortText(Prescription p) {
        if (p == null || p.type() == null) return null;
        return switch (p.type()) {
            case "reps" -> (p.sets() == null ? "" : p.sets() + " × ")
                    + (p.reps() != null ? String.valueOf(p.reps())
                            : p.repsMin() + "–" + p.repsMax());
            case "hold" -> (p.sets() == null ? "" : p.sets() + " × ") + seconds(p.holdS());
            case "time" -> duration(p.durationS());
            case "interval" -> p.repeats() + " × " + duration(p.workS());
            case "distance" -> p.distanceM() == null ? null
                    : p.distanceM() >= 1000 ? fmtKm(p.distanceM()) + " km" : p.distanceM() + " m";
            default -> null;
        };
    }

    private static String sets(Integer sets) {
        if (sets == null) return "";
        return sets == 1 ? "1 serie de " : sets + " series de ";
    }

    private static String seconds(Integer s) {
        return s == null ? "el tiempo indicado" : s + " s";
    }

    static String duration(Integer s) {
        if (s == null) return "el tiempo indicado";
        if (s < 60) return s + " s";
        int m = s / 60;
        int r = s % 60;
        return r == 0 ? m + " min" : m + " min " + r + " s";
    }

    private static String pace(int secPerKm) {
        return secPerKm / 60 + ":" + String.format("%02d", secPerKm % 60);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v).replace('.', ',');
    }

    private static String fmtKm(int meters) {
        return fmt(Math.round(meters / 100.0) / 10.0);
    }
}
