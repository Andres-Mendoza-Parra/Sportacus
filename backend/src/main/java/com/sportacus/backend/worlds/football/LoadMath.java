package com.sportacus.backend.worlds.football;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Matemática de carga del fútbol (pura, sin BD).
 * <ul>
 * <li>Carga de sesión = RPE × minutos (sRPE, Foster et al., 2001).</li>
 * <li>ACWR = carga aguda (últimos 7 días) ÷ carga crónica (media semanal de los últimos 28 días), modelo de medias
 * acopladas de Gabbett (2016). Solo se calcula con ≥ 21 días de historial; con 21–27 días la media semanal se
 * calcula sobre los días disponibles para no inflar el cociente.</li>
 * </ul>
 */
public final class LoadMath {

    public static final int MIN_HISTORY_DAYS = 21;
    public static final int CHRONIC_DAYS = 28;

    private LoadMath() {
    }

    /** Carga de un día (puede haber varias sesiones el mismo día). */
    public record DayLoad(LocalDate date, double load) {
    }

    public record WeekLoad(LocalDate monday, double total) {
    }

    public record Acwr(double acute, double chronicWeekly, double ratio, int historyDays) {
    }

    /** Suma de carga en [to - days + 1, to]. */
    public static double sumWindow(List<DayLoad> loads, LocalDate to, int days) {
        LocalDate from = to.minusDays(days - 1L);
        return loads.stream().filter(l -> !l.date().isBefore(from) && !l.date().isAfter(to))
                .mapToDouble(DayLoad::load).sum();
    }

    /**
     * ACWR a fecha {@code today}. {@code firstDay} = primer día con sesión registrada en el mundo.
     * Vacío si no hay historial suficiente o la carga crónica es 0.
     */
    public static Optional<Acwr> acwr(List<DayLoad> loads, LocalDate today, LocalDate firstDay) {
        if (firstDay == null || firstDay.isAfter(today)) return Optional.empty();
        int history = (int) ChronoUnit.DAYS.between(firstDay, today) + 1;
        if (history < MIN_HISTORY_DAYS) return Optional.empty();
        int window = Math.min(CHRONIC_DAYS, history);
        double chronicWeekly = sumWindow(loads, today, window) * 7.0 / window;
        if (chronicWeekly <= 0) return Optional.empty();
        double acute = sumWindow(loads, today, 7);
        double ratio = Math.round(acute / chronicWeekly * 100) / 100.0;
        return Optional.of(new Acwr(acute, Math.round(chronicWeekly), ratio, history));
    }

    /** Zona: baja (&lt; 0,8) · optima (0,8–1,3) · precaucion (1,3–1,5) · riesgo (&gt; 1,5). */
    public static String zone(double ratio) {
        if (ratio < 0.8) return "baja";
        if (ratio <= 1.3) return "optima";
        if (ratio <= 1.5) return "precaucion";
        return "riesgo";
    }

    public static String zoneTone(String zone) {
        return switch (zone) {
            case "baja" -> "info";
            case "optima" -> "good";
            case "precaucion" -> "warn";
            default -> "bad";
        };
    }

    /** Mensaje para el usuario según la zona. */
    public static String zoneMessage(double ratio) {
        String r = fmt(ratio);
        return switch (zone(ratio)) {
            case "baja" -> "Carga baja (ACWR " + r + "): tu semana fue más suave que tu media; puedes subir un poco el volumen.";
            case "optima" -> "Zona óptima (ACWR " + r + "): carga equilibrada respecto a tu último mes.";
            case "precaucion" -> "Precaución (ACWR " + r + "): esta semana cargaste bastante más que tu media; no añadas sesiones extra.";
            default -> "Riesgo (ACWR " + r + "): baja la intensidad y prioriza recuperar; el pico de carga se asocia a más lesiones.";
        };
    }

    /** Totales semanales (lunes a domingo) de las últimas {@code weeks} semanas, la última es la actual. */
    public static List<WeekLoad> weeklyTotals(List<DayLoad> loads, LocalDate today, int weeks) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate from = monday.minusWeeks(weeks - 1L);
        List<WeekLoad> out = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            LocalDate start = from.plusWeeks(i);
            LocalDate end = start.plusDays(6);
            double sum = loads.stream().filter(l -> !l.date().isBefore(start) && !l.date().isAfter(end))
                    .mapToDouble(DayLoad::load).sum();
            out.add(new WeekLoad(start, sum));
        }
        return out;
    }

    public static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v).replace('.', ',');
    }
}
