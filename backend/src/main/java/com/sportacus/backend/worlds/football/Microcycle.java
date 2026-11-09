package com.sportacus.backend.worlds.football;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Microciclo semanal organizado alrededor del día de partido (MD). Puro: no conoce movimientos ni BD.
 * <p>
 * Reglas (días respecto al partido más cercano):
 * <ul>
 * <li>MD+1 recuperación activa · MD-4 fuerza y prevención · MD-3 resistencia específica · MD-2 velocidad y técnica ·
 * MD-1 activación. Fuerza y resistencia exigen ≥ 3 días hasta el partido y ≥ 2 desde el anterior.</li>
 * <li>Los entrenamientos de equipo ocupan primero MD-3, MD-2, MD-4, MD-5; las sesiones propias los complementan
 * (menos acondicionamiento y más prevención).</li>
 * <li>Con dos partidos el microciclo se comprime: recuperación, prevención y activación.</li>
 * <li>Sin partido fijo: reparto genérico en los días sugeridos.</li>
 * </ul>
 */
public final class Microcycle {

    private Microcycle() {
    }

    public enum Type {
        PARTIDO("partido", "Partido"),
        EQUIPO("equipo", "Entrenamiento con tu equipo"),
        FUERZA("fuerza", "Fuerza y prevención"),
        RESISTENCIA("resistencia", "Resistencia específica"),
        VELOCIDAD("velocidad", "Velocidad y técnica"),
        ACTIVACION("activacion", "Activación prepartido"),
        RECUPERACION("recuperacion", "Recuperación activa"),
        PREVENCION("prevencion", "Prevención y técnica");

        public final String kind;
        public final String label;

        Type(String kind, String label) {
            this.kind = kind;
            this.label = label;
        }

        public static Type ofKind(String kind) {
            for (Type t : values()) if (t.kind.equals(kind)) return t;
            return null;
        }
    }

    /**
     * @param matchDay      día del partido principal (1=lunes … 7=domingo) o null si no es fijo
     * @param matches       partidos por semana (0–2)
     * @param teamTrainings entrenamientos con el equipo (0–4)
     * @param ownSessions   sesiones propias deseadas (1–4)
     * @param fallbackDays  días sugeridos para el reparto genérico (ya ordenados)
     */
    public record Input(Integer matchDay, int matches, int teamTrainings, int ownSessions, List<Integer> fallbackDays) {
    }

    /** @param md etiqueta "MD-2", "MD+1", "MD" o null sin partido fijo */
    public record Slot(Integer weekday, Type type, String md) {
        public String title() {
            return md == null ? type.label : md + " · " + type.label;
        }
    }

    public record Result(List<Slot> slots, int droppedOwn, List<Integer> matchDays) {
    }

    public static Result build(Input in) {
        int own = clamp(in.ownSessions(), 1, 4);
        int team = clamp(in.teamTrainings(), 0, 4);
        int matches = clamp(in.matches(), 0, 2);
        if (in.matchDay() == null || matches == 0) return generic(in, own, team, matches);

        int m1 = clamp(in.matchDay(), 1, 7);
        List<Integer> matchDays = new ArrayList<>(List.of(m1));
        if (matches == 2) matchDays.add(wrap(m1 - 3)); // segundo partido a mitad de semana
        List<Slot> slots = new ArrayList<>();
        Set<Integer> used = new LinkedHashSet<>(matchDays);
        for (int m : matchDays) slots.add(new Slot(m, Type.PARTIDO, "MD"));

        // Entrenamientos de equipo en sus días típicos.
        for (int i = 0; i < team; i++) {
            int[] pref = { 3, 2, 4, 5, 6, 1 };
            Integer d = bestDay(used, matchDays, pref, 2); // evita MD+1 si se puede
            if (d == null) d = bestDay(used, matchDays, pref, 1);
            if (d == null) break;
            used.add(d);
            slots.add(new Slot(d, Type.EQUIPO, label(d, matchDays)));
        }

        List<Type> priority = matches >= 2 ? List.of(Type.RECUPERACION, Type.PREVENCION, Type.ACTIVACION, Type.VELOCIDAD)
                : team == 0 ? List.of(Type.FUERZA, Type.VELOCIDAD, Type.RESISTENCIA, Type.RECUPERACION, Type.ACTIVACION)
                : team == 1 ? List.of(Type.FUERZA, Type.VELOCIDAD, Type.RECUPERACION, Type.ACTIVACION, Type.PREVENCION)
                : List.of(Type.FUERZA, Type.RECUPERACION, Type.ACTIVACION, Type.PREVENCION);
        int placed = 0;
        for (Type t : priority) {
            if (placed >= own) break;
            Integer d = dayFor(t, used, matchDays);
            if (d == null) continue;
            used.add(d);
            slots.add(new Slot(d, t, label(d, matchDays)));
            placed++;
        }
        // Relleno con prevención y técnica si aún faltan sesiones y quedan días válidos.
        while (placed < own) {
            Integer d = dayFor(Type.PREVENCION, used, matchDays);
            if (d == null) break;
            used.add(d);
            slots.add(new Slot(d, Type.PREVENCION, label(d, matchDays)));
            placed++;
        }
        slots.sort(Comparator.comparing(Slot::weekday));
        return new Result(slots, own - placed, matchDays);
    }

    private static Result generic(Input in, int own, int team, int matches) {
        List<Type> ownTypes = new ArrayList<>(team == 0
                ? List.of(Type.FUERZA, Type.VELOCIDAD, Type.RESISTENCIA, Type.PREVENCION)
                : List.of(Type.FUERZA, Type.PREVENCION, Type.VELOCIDAD, Type.RECUPERACION)).subList(0, own);
        int total = Math.min(7, own + team);
        List<Integer> days = new ArrayList<>(in.fallbackDays() == null ? List.of() : in.fallbackDays());
        for (int d = 1; days.size() < total && d <= 7; d++) if (!days.contains(d)) days.add(d);
        days = new ArrayList<>(days.subList(0, Math.min(total, days.size())));
        days.sort(Comparator.naturalOrder());
        List<Slot> slots = new ArrayList<>();
        int o = 0, t = 0;
        for (int i = 0; i < days.size(); i++) {
            // Alterna propia / equipo empezando por la propia (fuerza primero en la semana).
            boolean ownTurn = o < ownTypes.size() && (t >= team || i % 2 == 0);
            if (ownTurn) slots.add(new Slot(days.get(i), ownTypes.get(o++), null));
            else if (t < team) {
                slots.add(new Slot(days.get(i), Type.EQUIPO, null));
                t++;
            }
        }
        // Partidos sin día fijo: sesión sin día para poder registrarlos.
        for (int i = 0; i < matches; i++) slots.add(new Slot(null, Type.PARTIDO, null));
        return new Result(slots, ownTypes.size() - o, List.of());
    }

    /** Día libre que cumple la regla del tipo, prefiriendo su distancia canónica al partido. */
    static Integer dayFor(Type t, Set<Integer> used, List<Integer> matchDays) {
        return switch (t) {
            case FUERZA -> bestDay(used, matchDays, new int[] { 4, 5, 3, 6 }, 2);
            case RESISTENCIA -> bestDay(used, matchDays, new int[] { 3, 4, 5 }, 2);
            case VELOCIDAD -> bestDay(used, matchDays, new int[] { 2, 3 }, 2);
            case ACTIVACION -> bestDay(used, matchDays, new int[] { 1 }, 1);
            case PREVENCION -> bestDay(used, matchDays, new int[] { 2, 3, 4, 5 }, 1);
            case RECUPERACION -> {
                for (int m : matchDays) {
                    int d = wrap(m + 1);
                    if (!used.contains(d) && daysToNext(d, matchDays) >= 1) yield d;
                }
                yield null;
            }
            default -> null;
        };
    }

    private static Integer bestDay(Set<Integer> used, List<Integer> matchDays, int[] toNextPreference, int minSince) {
        for (int toNext : toNextPreference) {
            for (int d = 1; d <= 7; d++) {
                if (used.contains(d)) continue;
                if (daysToNext(d, matchDays) == toNext && daysSince(d, matchDays) >= minSince) return d;
            }
        }
        return null;
    }

    /** Días hasta el próximo partido (0 = es día de partido). */
    static int daysToNext(int day, List<Integer> matchDays) {
        int best = 7;
        for (int m : matchDays) best = Math.min(best, Math.floorMod(m - day, 7));
        return best;
    }

    /** Días desde el último partido (0 = es día de partido). */
    static int daysSince(int day, List<Integer> matchDays) {
        int best = 7;
        for (int m : matchDays) best = Math.min(best, Math.floorMod(day - m, 7));
        return best;
    }

    static String label(int day, List<Integer> matchDays) {
        int toNext = daysToNext(day, matchDays);
        int since = daysSince(day, matchDays);
        if (toNext == 0) return "MD";
        if (since <= 2 && since < toNext) return "MD+" + since;
        return "MD-" + toNext;
    }

    static int wrap(int d) {
        return Math.floorMod(d - 1, 7) + 1;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
