package com.sportacus.backend.worlds.calisthenics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reglas puras del mundo Calistenia (sin BD, probadas en {@code CalisthenicsRulesTest}).
 * <ul>
 * <li><b>Ubicación:</b> el escalón inicial de cada árbol sale de la marca máxima declarada o medida.
 * Regla general: se salta un escalón cuando la marca de una sola serie supera con holgura (~25 %) el
 * criterio de desbloqueo de ese escalón (3 series de X piden una serie máxima de ~1,25·X).</li>
 * <li><b>Desbloqueo:</b> {"type":"reps","sets":3,"reps":8} = al menos 3 series con ≥ 8 repeticiones;
 * {"type":"hold","sets":3,"seconds":30} = al menos 3 series de ≥ 30 s.</li>
 * <li><b>Reparto semanal:</b> rotación circular de árboles para que cada uno se entrene ≥ 2 veces.</li>
 * </ul>
 * El orden de dificultad de los escalones es un hecho de dominio público del entrenamiento con peso
 * corporal; los textos y umbrales son propios del equipo (docs/17).
 */
final class CalisthenicsRules {

    private CalisthenicsRules() {
    }

    /** Orden de los árboles dentro de una sesión: habilidad técnica primero (fresco), core al final. */
    static final List<String> SESSION_ORDER = List.of("parada_manos", "jale", "empuje", "fondos", "piernas", "core");

    /** Habilidad objetivo del onboarding → árbol que la contiene. */
    static final Map<String, String> SKILL_TREE = Map.of("primera_dominada", "jale", "muscle_up", "jale",
            "parada_manos", "parada_manos", "l_sit", "core", "pistol", "piernas", "fondos", "fondos");

    /** Marcas del usuario (onboarding o último test). handstand: nunca | pica | pared | libre. */
    record Maxes(int pushups, int pullups, int dips, int plankS, int squats, String handstand) {
    }

    /** Condiciones que limitan la ubicación inicial. */
    record Flags(boolean fragile, boolean lowerLimb, boolean senior, boolean wantsLSit) {
        static Flags none() {
            return new Flags(false, false, false, false);
        }
    }

    /** Escalón propuesto y la marca que lo justifica (para el texto del porqué). */
    record Placement(int step, String basis) {
    }

    /** Una serie registrada (repeticiones o segundos). */
    record SetLog(Integer reps, Integer holdS) {
        int value(boolean hold) {
            Integer v = hold ? holdS : reps;
            return v == null ? 0 : v;
        }
    }

    static String progressionKey(String tree) {
        return "cal_" + tree;
    }

    static String treeOf(String progressionKey) {
        return progressionKey.startsWith("cal_") ? progressionKey.substring(4) : progressionKey;
    }

    // ───────────────────────── Ubicación ─────────────────────────

    static Placement place(String tree, Maxes m, Flags f) {
        return switch (tree) {
            case "empuje" -> pushPlacement(m.pushups(), f);
            case "jale" -> pullPlacement(m.pullups(), m.pushups());
            case "fondos" -> dipPlacement(m.dips(), m.pushups());
            case "core" -> corePlacement(m.plankS(), m.dips(), f);
            case "piernas" -> legPlacement(m.squats(), f);
            case "parada_manos" -> handstandPlacement(m.handstand(), m.pushups());
            default -> new Placement(0, "sin datos");
        };
    }

    /** Desbloqueos: pared 20 · inclinada 15 · rodillas 15 · completa 12 · diamante 10 · declinada 12 · arquero 6. */
    static Placement pushPlacement(int p, Flags f) {
        String basis = p + (p == 1 ? " flexión completa" : " flexiones completas");
        if (p >= 45) return new Placement(7, basis);
        if (p >= 35) return new Placement(6, basis);
        if (p >= 25) return new Placement(5, basis);
        if (p >= 15) return new Placement(4, basis);
        if (p >= 5) return new Placement(3, basis);
        if (p >= 1) return new Placement(2, basis);
        // Sin flexiones completas: inclinada, o la pared si hay que cuidar las articulaciones.
        return new Placement(f.fragile() ? 0 : 1, "aún sin flexiones completas" + (f.fragile() ? " (empezamos suave)" : ""));
    }

    /** Desbloqueos: colgado 30 s · escapulares 10 · australiano 12 · negativas 5 · dominada 8 · al pecho 6 · L 6 · arquero 5. */
    static Placement pullPlacement(int u, int pushups) {
        String basis = u + (u == 1 ? " dominada" : " dominadas");
        if (u >= 18) return new Placement(8, basis);
        if (u >= 15) return new Placement(7, basis);
        if (u >= 13) return new Placement(6, basis);
        if (u >= 10) return new Placement(5, basis);
        if (u >= 3) return new Placement(4, basis);
        if (u >= 1) return new Placement(3, basis);
        // 0 dominadas: la fuerza de empuje indica cuánto peso corporal ya controlas.
        if (pushups >= 15) return new Placement(2, "0 dominadas pero buena fuerza general (" + pushups + " flexiones)");
        if (pushups >= 5) return new Placement(1, "0 dominadas y " + pushups + " flexiones");
        return new Placement(0, "0 dominadas: empezamos por el agarre");
    }

    /** Desbloqueos: silla 15 · soporte 30 s · negativos 5 · paralelas 10 · barra recta 10 · mochila 8. */
    static Placement dipPlacement(int d, int pushups) {
        String basis = d + (d == 1 ? " fondo" : " fondos");
        if (d >= 25) return new Placement(6, basis);
        if (d >= 18) return new Placement(5, basis);
        if (d >= 13) return new Placement(4, basis);
        if (d >= 5) return new Placement(3, basis);
        if (d >= 1) return new Placement(2, basis);
        if (pushups >= 10) return new Placement(1, "0 fondos pero " + pushups + " flexiones");
        return new Placement(0, "0 fondos");
    }

    /** Desbloqueos: dead bug 12 · plancha 45 s · hollow encogido 30 s · hollow 30 s. */
    static Placement corePlacement(int plankS, int dips, Flags f) {
        String basis = plankS + " s de plancha";
        int step;
        if (plankS >= 120) step = 3;
        else if (plankS >= 75) step = 2;
        else if (plankS >= 30) step = 1;
        else step = 0;
        // El L-sit exige además apoyo de brazos: solo si lo busca y ya hace fondos.
        if (f.wantsLSit() && plankS >= 60 && dips >= 10) {
            return new Placement(Math.max(step, 4), basis + " y " + dips + " fondos");
        }
        return new Placement(step, basis);
    }

    /** Desbloqueos: sentadilla 25 · zancada 12 · split 12 · búlgara 12. Pistol y camarón solo progresando. */
    static Placement legPlacement(int q, Flags f) {
        String basis = q + " sentadillas";
        int step;
        if (q >= 60) step = 4;
        else if (q >= 45) step = 3;
        else if (q >= 30) step = 2;
        else if (q >= 15) step = 1;
        else step = 0;
        if (f.lowerLimb() && step > 2) return new Placement(2, basis + " (tope por tus rodillas/tobillos)");
        if (f.senior() && step > 1) return new Placement(1, basis + " (tope por edad: priorizamos el control)");
        return new Placement(step, basis);
    }

    static Placement handstandPlacement(String level, int pushups) {
        String hs = level == null ? "nunca" : level;
        return switch (hs) {
            case "libre" -> new Placement(pushups >= 30 ? 5 : 4, "ya sostienes segundos sin pared");
            case "pared" -> new Placement(3, "ya aguantas en la pared");
            case "pica" -> new Placement(pushups >= 20 ? 2 : 1, "ya haces flexiones en pica");
            default -> new Placement(pushups >= 15 ? 1 : 0, "primera vez con invertidos");
        };
    }

    /** Nivel 1–5 = progreso medio de los árboles (escalón / último escalón). */
    static int level(Map<String, Integer> steps, Map<String, Integer> lastIndex) {
        if (steps.isEmpty()) return 1;
        double sum = 0;
        int n = 0;
        for (var e : steps.entrySet()) {
            int last = Math.max(1, lastIndex.getOrDefault(e.getKey(), 1));
            sum += Math.min(1.0, e.getValue() / (double) last);
            n++;
        }
        return Math.max(1, Math.min(5, 1 + (int) Math.round(sum / n * 4)));
    }

    // ───────────────────────── Desbloqueo ─────────────────────────

    static boolean isHold(Map<String, Object> criteria) {
        return "hold".equals(String.valueOf(criteria.get("type")));
    }

    static int criteriaSets(Map<String, Object> criteria) {
        return num(criteria.get("sets"), 3);
    }

    /** Repeticiones o segundos que pide el criterio por serie. */
    static int criteriaValue(Map<String, Object> criteria) {
        return isHold(criteria) ? num(criteria.get("seconds"), 30) : num(criteria.get("reps"), 8);
    }

    /** ¿Las series registradas cumplen el criterio de desbloqueo? */
    static boolean criteriaMet(Map<String, Object> criteria, List<SetLog> sets) {
        if (criteria == null || criteria.isEmpty()) return false;
        boolean hold = isHold(criteria);
        int target = criteriaValue(criteria);
        long ok = sets.stream().filter(s -> s.value(hold) >= target).count();
        return ok >= criteriaSets(criteria);
    }

    /** Lo que falta (repeticiones o segundos sumando las mejores series) para cumplir el criterio. */
    static int gap(Map<String, Object> criteria, List<SetLog> sets) {
        boolean hold = isHold(criteria);
        int target = criteriaValue(criteria);
        List<Integer> best = new ArrayList<>(sets.stream().map(s -> s.value(hold)).sorted(Comparator.reverseOrder()).toList());
        int gap = 0;
        for (int i = 0; i < criteriaSets(criteria); i++) {
            int v = i < best.size() ? best.get(i) : 0;
            gap += Math.max(0, target - v);
        }
        return gap;
    }

    /** Sesión muy por debajo de lo planificado: media < 60 % del mínimo prescrito. */
    static boolean farBelow(int plannedMin, List<SetLog> sets, boolean hold) {
        if (sets.isEmpty() || plannedMin <= 0) return false;
        double avg = sets.stream().mapToInt(s -> s.value(hold)).average().orElse(0);
        return avg < plannedMin * 0.6;
    }

    // ───────────────────────── Reparto semanal ─────────────────────────

    /** Árboles por sesión necesarios para que cada árbol se entrene al menos 2 veces por semana. */
    static int neededPerSession(int trees, int days) {
        return (int) Math.ceil(2.0 * trees / Math.max(1, days));
    }

    /**
     * Rotación circular: cada sesión toma {@code perSession} árboles seguidos de la lista (sin repetir en
     * el día). Con 2–3 días suele salir cuerpo completo; con 4–5, empuje/jale/piernas alternados. Se
     * limita a ~3 estímulos semanales por árbol para dejar recuperación.
     */
    static List<List<String>> schedule(List<String> trees, int days, int perSession) {
        int t = trees.size();
        int d = Math.max(1, days);
        int k = Math.max(1, Math.min(Math.min(perSession, t), Math.max(neededPerSession(t, d), (int) Math.floor(3.0 * t / d))));
        List<List<String>> out = new ArrayList<>();
        int cursor = 0;
        for (int i = 0; i < d; i++) {
            List<String> day = new ArrayList<>();
            for (int j = 0; j < k; j++) day.add(trees.get((cursor + j) % t));
            cursor = (cursor + k) % t;
            day.sort(Comparator.comparingInt(CalisthenicsRules::sessionRank));
            out.add(day);
        }
        return out;
    }

    static int sessionRank(String tree) {
        int i = SESSION_ORDER.indexOf(tree);
        return i < 0 ? SESSION_ORDER.size() : i;
    }

    static int num(Object o, int fallback) {
        return o instanceof Number n ? n.intValue() : fallback;
    }

    // ───────────────────────── Onboarding mínimo y Perfil 360 ─────────────────────────

    /** Pregunta del onboarding (opcional) que da la marca de cada árbol. */
    static final Map<String, String> MARK_QUESTION = Map.of("empuje", "c_pushups", "jale", "c_pullups",
            "fondos", "c_dips", "core", "c_plank", "piernas", "c_squats", "parada_manos", "c_handstand");

    /** Test → pregunta de marca que deja de ser desconocida al hacerlo. */
    static final Map<String, String> TEST_MARK = Map.of("cal_test_flexiones", "c_pushups",
            "cal_test_dominadas", "c_pullups", "cal_test_fondos", "c_dips", "cal_test_sentadillas", "c_squats");

    /** Árboles cuya marca no se respondió: se ubican en el escalón base seguro (marca 0). */
    static List<String> unknownMarkTrees(List<String> trees, java.util.function.Predicate<String> answered) {
        return trees.stream().filter(t -> MARK_QUESTION.containsKey(t) && !answered.test(MARK_QUESTION.get(t))).toList();
    }

    /** Aviso para el usuario cuando faltan marcas (null si no falta ninguna). */
    static String unknownMarksNotice(List<String> treeNames) {
        if (treeNames.isEmpty()) return null;
        return "Aún no sabemos tus marcas en " + String.join(", ", treeNames)
                + ": empezamos en un escalón base seguro. Haz el test del árbol o cuéntanos tus marcas para ajustarlo.";
    }

    /** Equipo de calistenia que ya declaró en su perfil (p_equipment), para precargar c_equipment. */
    static List<String> homeEquipmentFromProfile(List<String> profileEquipment) {
        if (profileEquipment == null || profileEquipment.isEmpty()) return List.of();
        List<String> out = profileEquipment.stream()
                .filter(e -> List.of("barra_dominadas", "paralelas", "anillas", "bandas").contains(e)).toList();
        return out.isEmpty() ? List.of("ninguno") : out;
    }

    /** Lugar sugerido según sus entornos del perfil (null si no aplica). */
    static String placeFromEnvironments(List<String> environments) {
        if (environments == null) return null;
        boolean home = environments.contains("casa");
        boolean park = environments.contains("parque");
        if (home && park) return "ambos";
        if (park) return "parque";
        if (home) return "casa";
        return null;
    }

    /** Movimiento con saltos o aterrizajes (pliometría, burpees…): se excluye en casa si no se puede saltar. */
    static boolean jumpy(String key, String name) {
        String s = ((key == null ? "" : key) + " " + (name == null ? "" : name)).toLowerCase(java.util.Locale.ROOT);
        return s.contains("salto") || s.contains("saltar") || s.contains("jump") || s.contains("burpee")
                || s.contains("pliom");
    }

    /** Movimientos a los que se suele subir con un salto o que terminan cayendo a los pies. */
    static final java.util.Set<String> HOP_ENTRY = java.util.Set.of("dominada_negativa", "fondos_negativos",
            "soporte_paralelas", "parada_manos_intentos", "parada_manos_libre", "transicion_muscle_up");

    /** Indicación para entrenar sin saltos ni golpes en casa (null si el movimiento no lo necesita). */
    static String quietCue(String movementKey) {
        if (!HOP_ENTRY.contains(movementKey)) return null;
        return movementKey.startsWith("parada")
                ? "Sin saltos: sube caminando con los pies por la pared y baja con control, sin golpes"
                : "Sin saltar: sube apoyándote en una silla y baja a los pies con suavidad";
    }

    /** Minutos de una sesión exprés (para quien tiene poco margen, p. ej. con hijos pequeños). */
    static final int SHORT_SESSION_MIN = 25;

    static int sessionMinutes(int profileMinutes, boolean shortSessions) {
        int m = Math.max(20, profileMinutes);
        return shortSessions ? Math.min(m, SHORT_SESSION_MIN) : m;
    }
}
