package com.sportacus.backend.worlds.health;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reglas puras de clasificación inicial del mundo Salud (sin BD, probadas en {@code HealthTriageTest}).
 * No diagnostican: solo eligen un programa educativo de ejercicio general y su fase de entrada.
 */
public final class HealthTriage {

    public static final Set<String> ZONE_TRACKS = Set.of("rodilla", "cadera", "lumbar", "hombro", "tobillo");
    static final Set<String> CVMR = Set.of("hipertension", "diabetes", "cardiopatia", "renal");

    private HealthTriage() {
    }

    /** Zona del chequeo de salud → ruta del mundo (null si no hay ruta específica). */
    public static String trackForZone(String zone) {
        if (zone == null) return null;
        return switch (zone) {
            case "rodilla", "cadera", "lumbar", "hombro", "tobillo" -> zone;
            case "pie" -> "tobillo";
            case "dorsal", "cervical" -> "movilidad_general";
            default -> null;
        };
    }

    /** Primera zona del chequeo con ruta propia del mundo (para precargar la pregunta de zona). */
    public static String zoneFromHealth(List<String> injuredZones) {
        for (String z : injuredZones) {
            String t = trackForZone(z);
            if (t != null && ZONE_TRACKS.contains(t)) return t;
        }
        return null;
    }

    /**
     * Ruta: zona con molestia → ruta de la zona; edad ≥ 65 o caídas/inestabilidad → adulto mayor;
     * hipertensión, diabetes o cardiopatía → cardiometabólica; si no, movilidad y postura.
     */
    public static String chooseTrack(String zoneAnswer, List<String> injuredZones, int age, boolean falls,
            Set<String> healthTags, String reason) {
        String zone = trackForZone(zoneAnswer);
        if (zone == null && (zoneAnswer == null || "ninguna".equals(zoneAnswer))) {
            zone = zoneFromHealth(injuredZones);
        }
        if (zone != null && ZONE_TRACKS.contains(zone)) return zone;
        if (age >= 65 || falls) return "adulto_mayor";
        if (healthTags.stream().anyMatch(CVMR::contains)) return "cardiometabolico";
        if ("postura".equals(reason) || "movilidad_general".equals(zone)) return "movilidad_general";
        return "movilidad_general";
    }

    /**
     * Fase de entrada (índice 0-based). Siempre conservadora: ante la duda, fase 1.
     *
     * @param onset menos_6s | 6s_3m | mas_3m | null
     */
    public static int initialPhase(String track, String reason, String onset, int painRest, int painActivity,
            boolean postOpLocked, boolean mustRefer, boolean inactive, boolean falls, int age, String severity) {
        int pain = Math.max(painRest, painActivity);
        if (mustRefer || postOpLocked || "postoperatorio".equals(reason)) return 0;
        if (pain >= 4 || "grave".equals(severity)) return 0;
        if ("menos_6s".equals(onset) || "lesion_reciente".equals(reason)) return 0;
        return switch (track) {
            case "adulto_mayor" -> falls || age >= 80 || inactive ? 0 : 1;
            case "cardiometabolico", "movilidad_general" -> inactive ? 0 : 1;
            default -> "mas_3m".equals(onset) || "6s_3m".equals(onset) || onset == null ? 1 : 0;
        };
    }

    /**
     * Banda de normas que corresponde al valor (misma semántica que el núcleo: grupos por sexo/edad con
     * ageMax inclusivo; "min" inclusivo y "max" exclusivo; si ningún grupo aplica, bandas generales).
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> matchBand(Map<String, Object> norms, double value, int age, String sex) {
        if (norms == null) return null;
        List<Map<String, Object>> bands = null;
        if (norms.get("groups") instanceof List<?> groups) {
            for (Object g : groups) {
                Map<String, Object> gm = (Map<String, Object>) g;
                Object gs = gm.get("sex");
                if (gs != null && !gs.equals(sex)) continue;
                boolean ageBound = gm.containsKey("ageMin") || gm.containsKey("ageMax");
                if (age < 0 ? ageBound : (age < num(gm.get("ageMin"), 0) || age > num(gm.get("ageMax"), 200))) continue; // edad desconocida: no se aplican normas por edad
                bands = (List<Map<String, Object>>) gm.get("bands");
                break;
            }
        }
        if (bands == null && norms.get("bands") instanceof List<?> b) bands = (List<Map<String, Object>>) b;
        if (bands == null) return null;
        for (Map<String, Object> band : bands) {
            if (value >= num(band.get("min"), Double.NEGATIVE_INFINITY)
                    && value < num(band.get("max"), Double.POSITIVE_INFINITY)) {
                return band;
            }
        }
        return null;
    }

    public static boolean isRisk(Map<String, Object> band) {
        return band != null && Boolean.TRUE.equals(band.get("risk"));
    }

    private static double num(Object o, double fallback) {
        return o instanceof Number n ? n.doubleValue() : fallback;
    }
}
