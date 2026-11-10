package com.sportacus.backend.worlds.running;

import java.util.Locale;

/**
 * Fórmulas de running de uso libre (docs/17 §Running), sin dependencias de Spring para poder probarlas solas.
 * <ul>
 * <li>Riegel (1981): T2 = T1 · (D2/D1)^1,06.</li>
 * <li>Cooper (1968): VO₂máx ≈ (metros en 12 min − 504,9) / 44,73.</li>
 * <li>Zonas de ritmo a partir del ritmo de 5K (rp): suave rp+60…90 s/km, tempo rp+15…25, intervalos rp−5…0.</li>
 * </ul>
 */
public final class RunningMath {

    public static final double RIEGEL_EXPONENT = 1.06;
    public static final double HALF_MARATHON_M = 21097.5;

    private RunningMath() {
    }

    /** Tiempo previsto (s) en {@code d2} metros a partir de {@code t1} segundos en {@code d1} metros. */
    public static double riegel(double t1, double d1, double d2) {
        return t1 * Math.pow(d2 / d1, RIEGEL_EXPONENT);
    }

    /** VO₂máx estimado (ml/kg/min) a partir de los metros del test de Cooper. */
    public static double cooperVo2(double meters) {
        return (meters - 504.9) / 44.73;
    }

    /** 5K estimado (s) desde el test de Cooper: los 12 min se tratan como un esfuerzo de carrera (Riegel). */
    public static double fiveKFromCooper(double meters) {
        return riegel(720, meters, 5000);
    }

    /** Ritmos de entrenamiento (s/km) derivados del tiempo de 5K. */
    public record Paces(int fiveKS, int race, int easyMin, int easyMax, int tempo, int interval, int longRun,
            int tenK, int half) {

        public int easy() {
            return (easyMin + easyMax) / 2;
        }

        public String easyRange() {
            return pace(easyMin) + "–" + pace(easyMax) + " /km";
        }

        /**
         * Ritmos de entrenamiento multiplicados por {@code factor} (p. ej. 1,04 = un 4 % más lentos por la altitud).
         * El 5K de referencia ({@code fiveKS}) no cambia: es la marca declarada.
         */
        public Paces scaled(double factor) {
            if (factor == 1.0) return this;
            return new Paces(fiveKS, s(race, factor), s(easyMin, factor), s(easyMax, factor), s(tempo, factor),
                    s(interval, factor), s(longRun, factor), s(tenK, factor), s(half, factor));
        }

        private static int s(int pace, double factor) {
            return (int) Math.round(pace * factor);
        }
    }

    public static Paces paces(double fiveKSeconds) {
        int rp = (int) Math.round(fiveKSeconds / 5.0);
        int tenK = (int) Math.round(riegel(fiveKSeconds, 5000, 10000) / 10.0);
        int half = (int) Math.round(riegel(fiveKSeconds, 5000, HALF_MARATHON_M) / (HALF_MARATHON_M / 1000));
        return new Paces((int) Math.round(fiveKSeconds), rp, rp + 60, rp + 90, rp + 20, rp - 3, rp + 80, tenK, half);
    }

    /** 330 → "5:30". */
    public static String pace(int secPerKm) {
        return String.format(Locale.ROOT, "%d:%02d", secPerKm / 60, secPerKm % 60);
    }

    /** 1530 → "25:30"; 4000 → "1:06:40". */
    public static String time(double seconds) {
        int s = (int) Math.round(seconds);
        if (s >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** "6,5" con coma decimal y sin ceros sobrantes. */
    public static String km(double km) {
        double r = Math.round(km * 10) / 10.0;
        return (r == Math.floor(r) ? String.valueOf((int) r) : String.format(Locale.ROOT, "%.1f", r)).replace('.', ',');
    }

    /** Duración legible: 90 → "90 s", 120 → "2 min", 150 → "2,5 min". */
    public static String duration(int seconds) {
        if (seconds < 90 && seconds % 60 != 0) return seconds + " s";
        double min = seconds / 60.0;
        return km(min) + " min";
    }
}
