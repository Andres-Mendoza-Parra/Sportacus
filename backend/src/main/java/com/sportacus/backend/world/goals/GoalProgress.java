package com.sportacus.backend.world.goals;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.sportacus.backend.world.metrics.MetricRepository;

/**
 * Avance de una meta a partir de su {@code target} (lo que cada mundo guarda en {@code user_world_goal.target}).
 * Reglas puras y genéricas: no conocen los mundos, solo las formas de objetivo que generan.
 * <ul>
 * <li>{@code {progression, step}}: llegar a un escalón (mejor escalón alcanzado).</li>
 * <li>{@code {metric, min|seconds|reps}} / {@code {metric, max}} / {@code {metric, value}}: umbral de una métrica
 * (con {@code movement}, solo los registros de ese movimiento); {@code value} sigue la dirección de la métrica.</li>
 * <li>{@code {metric, improvePct}}: mejorar un % respecto al punto de partida.</li>
 * <li>{@code {metric, gain}}: sumar N a un contador (p. ej. escalones desbloqueados).</li>
 * <li>{@code {metric, min, max, weeks}}: N semanas seguidas con el valor dentro del rango.</li>
 * <li>{@code {metric, weeklySum|weeklyMinutes}} o {@code {minutesPerWeek}}: total semanal.</li>
 * <li>{@code {sessionsPerWeek, weeks}}, {@code {sessions}}, {@code {weeks, movements}}: constancia.</li>
 * <li>{@code {km[, minutes]}}: distancia (o minutos seguidos) en una sesión.</li>
 * </ul>
 * Lo demás (p. ej. series por músculo) no es medible: la persona la marca como lograda.
 */
public final class GoalProgress {

    private static final double EPS = 1e-6;

    private GoalProgress() {
    }

    public record Sample(double value, OffsetDateTime at, String movement) {
    }

    /** Una sesión registrada del mundo, resumida. */
    public record SessionFact(LocalDate day, Integer durationMin, Double distanceM, Integer maxBlockDurationS,
            Double maxBlockDistanceM, Set<String> movements) {
    }

    /** Datos que necesita el cálculo (los carga {@link GoalService} de forma perezosa). */
    public interface Data {
        int bestStep(String progressionKey);

        int sumSteps();

        /** Registros de la métrica en el mundo, del más antiguo al más reciente. */
        List<Sample> metric(String metricKey);

        /** "up", "down" o "range". */
        String direction(String metricKey);

        /** Sesiones del mundo, de la más antigua a la más reciente. */
        List<SessionFact> sessions();
    }

    /**
     * @param baseline punto de partida que conviene guardar si la meta aún no lo tiene (null si no aplica)
     */
    public record Result(boolean measurable, Integer pct, String text, boolean reached, Double baseline) {

        static Result manual() {
            return new Result(false, null, null, false, null);
        }
    }

    public static Result evaluate(Map<String, Object> target, OffsetDateTime createdAt, Double baseline, Data d,
            LocalDate today) {
        if (target == null || target.isEmpty()) return Result.manual();
        LocalDate since = createdAt == null ? LocalDate.MIN : createdAt.atZoneSameInstant(MetricRepository.ZONE).toLocalDate();
        OffsetDateTime createdMoment = createdAt == null ? OffsetDateTime.MIN : createdAt;

        if (target.get("progression") instanceof String prog && num(target, "step") != null) {
            return progression(prog, num(target, "step").intValue(), baseline, d);
        }
        if (target.get("metric") instanceof String metric) {
            return metric(metric, target, createdMoment, since, baseline, d, today);
        }
        if (num(target, "sessionsPerWeek") != null && num(target, "weeks") != null) {
            int perWeek = num(target, "sessionsPerWeek").intValue();
            int weeks = num(target, "weeks").intValue();
            Map<LocalDate, Integer> counts = new TreeMap<>();
            for (SessionFact s : d.sessions()) {
                if (!s.day().isBefore(since)) counts.merge(weekOf(s.day()), 1, Integer::sum);
            }
            Set<LocalDate> ok = new TreeSet<>();
            counts.forEach((w, c) -> {
                if (c >= perWeek) ok.add(w);
            });
            return consecutive(ok, weeks, today, since);
        }
        if (num(target, "sessions") != null) {
            int goal = num(target, "sessions").intValue();
            long done = d.sessions().stream().filter(s -> !s.day().isBefore(since)).count();
            return new Result(true, pct(done, goal), done + " de " + goal + " sesiones", done >= goal, null);
        }
        if (num(target, "minutesPerWeek") != null) {
            double goal = num(target, "minutesPerWeek");
            Map<LocalDate, Double> sums = new TreeMap<>();
            for (SessionFact s : d.sessions()) {
                if (s.durationMin() != null) sums.merge(weekOf(s.day()), s.durationMin().doubleValue(), Double::sum);
            }
            return weekly(sums, goal, today, since, "min");
        }
        if (num(target, "weeks") != null && target.get("movements") instanceof Collection<?> moves) {
            Set<String> wanted = new TreeSet<>();
            moves.forEach(m -> wanted.add(String.valueOf(m)));
            Set<LocalDate> ok = new TreeSet<>();
            for (SessionFact s : d.sessions()) {
                if (s.day().isBefore(since)) continue;
                if (s.movements().stream().anyMatch(wanted::contains)) ok.add(weekOf(s.day()));
            }
            return consecutive(ok, num(target, "weeks").intValue(), today, since);
        }
        if (num(target, "km") != null) {
            double km = num(target, "km");
            Double minutes = num(target, "minutes");
            double bestM = 0;
            double bestContinuousS = 0;
            for (SessionFact s : d.sessions()) {
                if (s.day().isBefore(since)) continue;
                bestM = Math.max(bestM, Math.max(nz(s.distanceM()), nz(s.maxBlockDistanceM())));
                bestContinuousS = Math.max(bestContinuousS, s.maxBlockDurationS() == null ? 0 : s.maxBlockDurationS());
            }
            double ratio = bestM / (km * 1000);
            String text = bestM > 0 ? "Tu mejor distancia: " + fmt(bestM / 1000) + " de " + fmt(km) + " km" : null;
            if (minutes != null) {
                double minRatio = bestContinuousS / (minutes * 60);
                if (minRatio > ratio) {
                    ratio = minRatio;
                    text = "Tu mejor tramo seguido: " + fmt(Math.floor(bestContinuousS / 60)) + " de " + fmt(minutes) + " min";
                }
            }
            if (text == null) text = "Aún sin registros";
            return new Result(true, pct(ratio), text, ratio >= 1, null);
        }
        return Result.manual();
    }

    private static Result progression(String prog, int step, Double baseline, Data d) {
        int cur = d.bestStep(prog);
        double base = baseline != null ? baseline : cur;
        boolean reached = cur >= step;
        Integer p;
        if (reached) p = 100;
        else if (step > base) p = pct((cur - base) / (step - base));
        else p = pct((double) cur / Math.max(1, step));
        return new Result(true, p, "Escalón " + (cur + 1) + " de " + (step + 1), reached, baseline == null ? (double) cur : null);
    }

    private static Result metric(String metric, Map<String, Object> target, OffsetDateTime created, LocalDate since,
            Double baseline, Data d, LocalDate today) {
        String movement = target.get("movement") instanceof String m ? m : null;
        List<Sample> all = d.metric(metric).stream().filter(s -> movement == null || movement.equals(s.movement())).toList();
        List<Sample> after = all.stream().filter(s -> !s.at().isBefore(created)).toList();
        boolean down = "down".equals(d.direction(metric));
        Sample latest = all.isEmpty() ? null : all.get(all.size() - 1);

        // Suma semanal (OMS y similares).
        Double weeklyGoal = num(target, "weeklySum") != null ? num(target, "weeklySum") : num(target, "weeklyMinutes");
        if (weeklyGoal != null) {
            Map<LocalDate, Double> sums = new TreeMap<>();
            for (Sample s : all) sums.merge(weekOf(day(s)), s.value(), Double::sum);
            return weekly(sums, weeklyGoal, today, since, "min");
        }
        // Rango sostenido N semanas.
        if (num(target, "min") != null && num(target, "max") != null && num(target, "weeks") != null) {
            double lo = num(target, "min");
            double hi = num(target, "max");
            Map<LocalDate, Double> lastOfWeek = new TreeMap<>();
            for (Sample s : after) lastOfWeek.put(weekOf(day(s)), s.value());
            Set<LocalDate> ok = new TreeSet<>();
            lastOfWeek.forEach((w, v) -> {
                if (v >= lo && v <= hi) ok.add(w);
            });
            return consecutive(ok, num(target, "weeks").intValue(), today, since);
        }
        // Contador que debe crecer N (p. ej. escalones desbloqueados).
        if (num(target, "gain") != null) {
            double gain = num(target, "gain");
            Double suggested = null;
            double base;
            if (baseline != null) {
                base = baseline;
            } else {
                Sample before = lastBefore(all, created);
                base = before != null ? before.value() : d.sumSteps();
                suggested = base;
            }
            double cur = after.isEmpty() ? base : Math.max(base, after.get(after.size() - 1).value());
            double done = Math.max(0, cur - base);
            return new Result(true, pct(done / gain), "+" + fmt(done) + " de +" + fmt(gain), done >= gain, suggested);
        }
        // Mejorar un % respecto al punto de partida.
        if (num(target, "improvePct") != null) {
            double improve = num(target, "improvePct");
            Double base = baseline;
            Double suggested = null;
            Sample baseSample = null;
            if (base == null) {
                baseSample = lastBefore(all, created);
                if (baseSample == null && !after.isEmpty()) baseSample = after.get(0);
                if (baseSample == null) {
                    return new Result(true, 0, "Haz el test o registra una sesión para fijar tu punto de partida", false, null);
                }
                base = baseSample.value();
                suggested = base;
            }
            double goal = down ? base * (1 - improve / 100) : base * (1 + improve / 100);
            Double best = null;
            for (Sample s : after) {
                if (s == baseSample) continue;
                best = best == null ? s.value() : down ? Math.min(best, s.value()) : Math.max(best, s.value());
            }
            double cur = best == null ? base : best;
            boolean reached = best != null && (down ? cur <= goal + EPS : cur >= goal - EPS);
            double ratio = goal == base ? 1 : (cur - base) / (goal - base);
            String text = "Partida: " + fmt(base) + " · meta: " + fmt(goal) + (best == null ? "" : " · tu mejor: " + fmt(best));
            return new Result(true, reached ? 100 : pct(ratio), text, reached, suggested);
        }
        // Umbral.
        Double min = firstNum(target, "min", "seconds", "reps");
        Double max = num(target, "max");
        Double value = num(target, "value");
        if (value != null) {
            if (down) max = value;
            else min = value;
        }
        if (min != null) {
            final double goal = min;
            boolean reached = after.stream().anyMatch(s -> s.value() >= goal - EPS);
            String text = latest == null ? "Aún sin registros" : "Último: " + fmt(latest.value()) + " · meta: " + fmt(goal) + " o más";
            Integer p = reached ? 100 : latest == null ? 0 : pct(latest.value() / goal);
            return new Result(true, p, text, reached, null);
        }
        if (max != null) {
            final double goal = max;
            boolean reached = after.stream().anyMatch(s -> s.value() <= goal + EPS);
            Double suggested = null;
            double base;
            if (baseline != null) {
                base = baseline;
            } else {
                base = all.isEmpty() ? Double.NaN : all.get(0).value();
                if (!all.isEmpty()) suggested = base;
            }
            String text = latest == null ? "Aún sin registros" : "Último: " + fmt(latest.value()) + " · meta: " + fmt(goal) + " o menos";
            Integer p;
            if (reached) p = 100;
            else if (latest == null || Double.isNaN(base)) p = 0;
            else if (base > goal) p = pct((base - latest.value()) / (base - goal));
            else p = 0;
            return new Result(true, p, text, reached, suggested);
        }
        return Result.manual();
    }

    // ── Semanas ───────────────────────────────────────────────

    static LocalDate weekOf(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static LocalDate day(Sample s) {
        return s.at().atZoneSameInstant(MetricRepository.ZONE).toLocalDate();
    }

    /**
     * Semanas seguidas que cumplen, contando hacia atrás desde esta semana (si aún no cumple, desde la anterior: la
     * semana en curso no rompe la racha). Solo semanas desde la creación de la meta.
     */
    static Result consecutive(Set<LocalDate> okWeeks, int weeks, LocalDate today, LocalDate since) {
        LocalDate first = since.equals(LocalDate.MIN) ? LocalDate.MIN : weekOf(since);
        int best = 0;
        // La mejor racha histórica (si ya se cumplió una vez, la meta está lograda aunque luego se corte).
        int run = 0;
        LocalDate prev = null;
        for (LocalDate w : new TreeSet<>(okWeeks)) {
            if (w.isBefore(first)) continue;
            run = prev != null && prev.plusWeeks(1).equals(w) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = w;
        }
        LocalDate w = weekOf(today);
        if (!okWeeks.contains(w)) w = w.minusWeeks(1);
        int current = 0;
        while (okWeeks.contains(w) && !w.isBefore(first)) {
            current++;
            w = w.minusWeeks(1);
        }
        boolean reached = best >= weeks;
        int shown = reached ? weeks : current;
        return new Result(true, pct((double) shown / weeks), shown + " de " + weeks + " semanas seguidas", reached, null);
    }

    static Result weekly(Map<LocalDate, Double> sums, double goal, LocalDate today, LocalDate since, String unit) {
        LocalDate first = since.equals(LocalDate.MIN) ? LocalDate.MIN : weekOf(since);
        boolean reached = sums.entrySet().stream().anyMatch(e -> !e.getKey().isBefore(first) && e.getValue() >= goal);
        double now = sums.getOrDefault(weekOf(today), 0.0);
        return new Result(true, reached ? 100 : pct(now / goal), "Esta semana: " + fmt(now) + " de " + fmt(goal) + " " + unit,
                reached, null);
    }

    // ── Utilidades ────────────────────────────────────────────

    private static Sample lastBefore(List<Sample> all, OffsetDateTime created) {
        Sample out = null;
        for (Sample s : all) if (s.at().isBefore(created)) out = s;
        return out;
    }

    static Double num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Double firstNum(Map<String, Object> m, String... keys) {
        for (String k : keys) if (num(m, k) != null) return num(m, k);
        return null;
    }

    private static double nz(Double v) {
        return v == null ? 0 : v;
    }

    static int pct(double ratio) {
        if (Double.isNaN(ratio)) return 0;
        return (int) Math.round(Math.max(0, Math.min(1, ratio)) * 100);
    }

    private static int pct(long done, int goal) {
        return pct(goal <= 0 ? 1 : (double) done / goal);
    }

    /** 12 → "12"; 4.567 → "4,6". */
    static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        return String.format(java.util.Locale.ROOT, "%.1f", v).replace('.', ',');
    }
}
