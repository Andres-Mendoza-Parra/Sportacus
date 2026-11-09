package com.sportacus.backend.worlds.health;

import java.util.Map;

/**
 * Reglas puras del semáforo de dolor (sin BD, probadas en {@code PainMonitoringTest}).
 *
 * <p>Basado en el modelo de monitorización del dolor de Silbernagel et al. (Am J Sports Med 2007;35(6):897-906),
 * que tolera dolor ≤ 5/10 durante la actividad si a la mañana siguiente vuelve a su nivel basal. Sportacus lo
 * aplica de forma MÁS CONSERVADORA (docs/17 §4), porque no hay un fisioterapeuta supervisando:
 * <ul>
 * <li><b>Verde:</b> dolor durante/después ≤ 3/10 y, a las 24 h de la sesión anterior, ≤ 3/10 o no peor que su
 * línea base → cuenta para avanzar de fase.</li>
 * <li><b>Amarillo:</b> dolor 4–5/10 (o 24 h algo por encima de la base) → se mantiene la fase y se baja el
 * volumen un 20 %.</li>
 * <li><b>Rojo:</b> dolor &gt; 5/10, o a las 24 h &gt; 3 puntos por encima de la base o &gt; 5/10 → se retrocede una fase
 * y se baja el volumen; dos rojos seguidos → se recomienda consultar a un profesional.</li>
 * </ul>
 * El avance exige N sesiones verdes CONSECUTIVAS (criterio {@code sessions} del escalón de la progresión).
 */
public final class PainMonitoring {

    /** Umbral de "dolor aceptable" para avanzar (más estricto que el 5/10 del modelo original). */
    public static final int GREEN_MAX = 3;
    /** Por encima de este valor la sesión es roja. */
    public static final int RED_ABOVE = 5;
    /** Subida a las 24 h respecto a la línea base que se considera roja. */
    public static final int NEXT_DAY_RED_DELTA = 3;
    public static final double VOLUME_STEP = 0.2;
    public static final double VOLUME_MIN = 0.6;
    /** Al entrar en una fase nueva se empieza con el 80 % del volumen. */
    public static final double VOLUME_NEW_PHASE = 0.8;

    private PainMonitoring() {
    }

    public enum Light {
        VERDE(0), AMARILLO(1), ROJO(2), SIN_DATOS(-1);

        public final int code;

        Light(int code) {
            this.code = code;
        }

        public String key() {
            return name().toLowerCase();
        }

        static Light worst(Light a, Light b) {
            if (a == SIN_DATOS) return b;
            if (b == SIN_DATOS) return a;
            return a.code >= b.code ? a : b;
        }

        public static Light ofCode(Double code) {
            if (code == null) return SIN_DATOS;
            return switch ((int) Math.round(code)) {
                case 0 -> VERDE;
                case 1 -> AMARILLO;
                case 2 -> ROJO;
                default -> SIN_DATOS;
            };
        }
    }

    /**
     * Datos de la sesión que se evalúa.
     *
     * @param painBefore   dolor en reposo al empezar (línea base de esta sesión)
     * @param painAfter    dolor al terminar
     * @param maxBlockPain máximo dolor registrado en los bloques
     * @param prevBaseline dolor antes de la sesión anterior (su línea base)
     * @param prevNextDay  dolor a las 24 h de la sesión anterior (null si no se registró)
     * @param rpe          esfuerzo percibido 1–10
     */
    public record Input(Integer painBefore, Integer painAfter, Integer maxBlockPain, Integer prevBaseline,
            Integer prevNextDay, Integer rpe) {

        /** Peor dolor durante o después de la sesión (null si no hay datos). */
        public Integer during() {
            Integer m = null;
            for (Integer v : new Integer[] { painAfter, maxBlockPain }) {
                if (v != null && (m == null || v > m)) m = v;
            }
            return m;
        }
    }

    /** Criterio de avance de una fase (unlock del escalón de la progresión). */
    public record Criteria(int maxDuring, int maxNextDay, Integer sessions, Integer rpeMax) {

        public static final Criteria DEFAULT = new Criteria(GREEN_MAX, GREEN_MAX, 6, null);

        /** Lee {"type":"pain","maxDuring":3,"maxNextDay":3,"sessions":6,"rpeMax":6}; "maintain" = sin avance. */
        public static Criteria from(Map<String, Object> unlock) {
            if (unlock == null || unlock.isEmpty()) return new Criteria(GREEN_MAX, GREEN_MAX, null, null);
            boolean maintain = "maintain".equals(unlock.get("type"));
            return new Criteria(
                    Math.min(GREEN_MAX, intOr(unlock.get("maxDuring"), GREEN_MAX)),
                    Math.min(GREEN_MAX, intOr(unlock.get("maxNextDay"), GREEN_MAX)),
                    maintain ? null : (Integer) intOrNull(unlock.get("sessions")),
                    intOrNull(unlock.get("rpeMax")));
        }

        public boolean canAdvance() {
            return sessions != null && sessions > 0;
        }
    }

    /** Estado vivo que guarda el módulo en user_world.state. */
    public record State(int phase, int greenStreak, int redStreak, double volumeFactor) {

        public static State initial(int phase) {
            return new State(phase, 0, 0, 1.0);
        }
    }

    /**
     * @param counted        la sesión sumó a la racha verde
     * @param advanceBlocked se cumplían las sesiones pero algo impide avanzar (aval, test, derivación)
     * @param referAdvised   dos sesiones rojas seguidas: recomendar consulta profesional
     */
    public record Decision(Light light, Light duringLight, Light nextDayLight, State state, boolean counted,
            boolean advanced, boolean regressed, boolean advanceBlocked, boolean referAdvised) {

        public boolean volumeChanged(State before) {
            return Math.abs(before.volumeFactor() - state.volumeFactor()) > 1e-9;
        }
    }

    /** Dolor durante/después: ≤ 3 verde · 4–5 amarillo · &gt; 5 rojo. */
    public static Light classifyDuring(Integer pain, int greenMax) {
        if (pain == null) return Light.SIN_DATOS;
        if (pain <= greenMax) return Light.VERDE;
        if (pain <= RED_ABOVE) return Light.AMARILLO;
        return Light.ROJO;
    }

    /**
     * Dolor a las 24 h frente a la línea base de esa sesión: vuelve a la base (o ≤ 3) verde; sube más de 3 puntos
     * o pasa de 5 rojo; en medio amarillo.
     */
    public static Light classifyNextDay(Integer baseline, Integer nextDay, int greenMax) {
        if (nextDay == null) return Light.SIN_DATOS;
        int base = baseline == null ? 0 : baseline;
        if (nextDay - base > NEXT_DAY_RED_DELTA || nextDay > RED_ABOVE) return Light.ROJO;
        if (nextDay <= greenMax || nextDay <= base) return Light.VERDE;
        return Light.AMARILLO;
    }

    /**
     * Decide el semáforo y su efecto.
     *
     * @param lastPhase   índice de la última fase de la ruta
     * @param maxPhase    fase máxima permitida ahora (p. ej. 0 si es postoperatorio sin aval)
     * @param extraGateOk criterios extra del escalón (p. ej. test de equilibrio) cumplidos
     */
    public static Decision decide(Input in, State s, Criteria c, int lastPhase, int maxPhase, boolean extraGateOk) {
        Light during = classifyDuring(in.during(), c.maxDuring());
        Light nextDay = classifyNextDay(in.prevBaseline(), in.prevNextDay(), c.maxNextDay());
        Light light = Light.worst(during, nextDay);
        int phase = Math.max(0, Math.min(s.phase(), lastPhase));
        int green = s.greenStreak();
        int red = s.redStreak();
        double vol = s.volumeFactor();
        boolean counted = false, advanced = false, regressed = false, blocked = false, refer = false;

        switch (light) {
            case VERDE -> {
                red = 0;
                vol = Math.min(1.0, vol + 0.1);
                boolean effortOk = c.rpeMax() == null || in.rpe() == null || in.rpe() <= c.rpeMax();
                if (effortOk) {
                    green++;
                    counted = true;
                }
                if (c.canAdvance() && green >= c.sessions() && phase < lastPhase) {
                    if (phase + 1 <= maxPhase && extraGateOk) {
                        phase++;
                        green = 0;
                        vol = VOLUME_NEW_PHASE;
                        advanced = true;
                    } else {
                        blocked = true;
                    }
                }
            }
            case AMARILLO -> {
                green = 0;
                red = 0;
                vol = Math.max(VOLUME_MIN, vol - VOLUME_STEP);
            }
            case ROJO -> {
                green = 0;
                red++;
                vol = Math.max(VOLUME_MIN, vol - VOLUME_STEP);
                if (phase > 0) {
                    phase--;
                    regressed = true;
                }
                refer = red >= 2;
            }
            case SIN_DATOS -> {
                // Sin registro de dolor no se avanza ni se retrocede.
            }
        }
        if (phase > maxPhase) {
            phase = Math.max(0, maxPhase);
        }
        vol = Math.round(vol * 100) / 100.0;
        return new Decision(light, during, nextDay, new State(phase, green, red, vol), counted, advanced, regressed,
                blocked, refer);
    }

    /**
     * Efecto inmediato del dolor a las 24 h (se registra al día siguiente de la sesión). La sesión ya se contó al
     * registrarse, así que el verde no suma de nuevo; amarillo reinicia la racha y baja volumen; rojo además
     * retrocede una fase y suma a la racha roja.
     */
    public static Decision decideNextDay(Integer baseline, Integer nextDay, State s, int greenMax) {
        Light light = classifyNextDay(baseline, nextDay, greenMax);
        int phase = s.phase();
        int green = s.greenStreak();
        int red = s.redStreak();
        double vol = s.volumeFactor();
        boolean regressed = false, refer = false;
        switch (light) {
            case AMARILLO -> {
                green = 0;
                vol = Math.max(VOLUME_MIN, vol - VOLUME_STEP);
            }
            case ROJO -> {
                green = 0;
                red++;
                vol = Math.max(VOLUME_MIN, vol - VOLUME_STEP);
                if (phase > 0) {
                    phase--;
                    regressed = true;
                }
                refer = red >= 2;
            }
            default -> {
                // Verde o sin datos: nada que cambiar.
            }
        }
        vol = Math.round(vol * 100) / 100.0;
        return new Decision(light, Light.SIN_DATOS, light, new State(phase, green, red, vol), false, false, regressed,
                false, refer);
    }

    private static int intOr(Object o, int fallback) {
        return o instanceof Number n ? n.intValue() : fallback;
    }

    private static Integer intOrNull(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }
}
