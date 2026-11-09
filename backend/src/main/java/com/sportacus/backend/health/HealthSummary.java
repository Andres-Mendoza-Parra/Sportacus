package com.sportacus.backend.health;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Resumen de salud que reciben todos los mundos. {@code activeTags} usa el vocabulario controlado
 * de {@code contraindication_tag}, el mismo de las contraindicaciones de los movimientos.
 */
public record HealthSummary(
        boolean screened,
        String riskLevel,
        List<String> flags,
        boolean requiresClearance,
        boolean clearanceConfirmed,
        boolean redFlags,
        Set<String> activeTags,
        List<ConditionView> conditions,
        OffsetDateTime screenedAt) {

    public static HealthSummary unknown() {
        return new HealthSummary(false, "bajo", List.of(), false, false, false, Set.of(), List.of(), null);
    }

    public record ConditionView(String tagKey, String label, String kind, String severity, String bodyZone) {
    }

    public boolean has(String tag) {
        return activeTags.contains(tag);
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    public boolean hasAny(String... tags) {
        for (String t : tags) if (activeTags.contains(t)) return true;
        return false;
    }

    /** Zonas del cuerpo con lesión/dolor activo. */
    public List<String> injuredZones() {
        return conditions.stream().filter(c -> "lesion".equals(c.kind())).map(ConditionView::tagKey).toList();
    }

    public boolean hasLowerLimbIssue() {
        return hasAny("rodilla", "cadera", "tobillo", "pie");
    }

    /** Peor severidad entre las lesiones activas (null si no hay). */
    public String worstInjurySeverity() {
        String worst = null;
        for (ConditionView c : conditions) {
            if (!"lesion".equals(c.kind()) || c.severity() == null) continue;
            if (worst == null || rank(c.severity()) > rank(worst)) worst = c.severity();
        }
        return worst;
    }

    /** La intensidad vigorosa requiere aval médico confirmado cuando el chequeo lo pidió. */
    public boolean vigorousAllowed() {
        return !(requiresClearance && !clearanceConfirmed);
    }

    /** Hay banderas rojas o síntomas cardiovasculares sin aval: la app debe detener planes y derivar. */
    public boolean mustRefer() {
        return redFlags || (hasFlag("cardio_sintomas") && !clearanceConfirmed);
    }

    private static int rank(String severity) {
        return switch (severity) {
            case "grave" -> 3;
            case "moderada" -> 2;
            default -> 1;
        };
    }
}
