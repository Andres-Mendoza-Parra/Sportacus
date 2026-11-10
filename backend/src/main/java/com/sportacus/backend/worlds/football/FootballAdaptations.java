package com.sportacus.backend.worlds.football;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.core.Factor;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.core.UserProfileData;
import com.sportacus.backend.world.core.Widget.Insight;

/**
 * Reglas puras del mundo Fútbol para el onboarding mínimo ("no presión") y el Perfil 360: valores por defecto
 * explicados cuando falta una respuesta, avisos de intensidad (modo prudente) y ajustes por el perfil derivado.
 * Cada ajuste cita el dato que lo motiva.
 */
final class FootballAdaptations {

    /** Posición genérica cuando el usuario no la indicó. */
    static final String GENERIC_POSITION = "campo";

    /** Patrones con choque, salto o caída: se evitan con anticoagulantes (riesgo de sangrado por golpes). */
    static final Set<String> CONTACT_OR_FALL_PATTERNS = Set.of("aereo", "pliometria", "portero_lateral");

    private FootballAdaptations() {
    }

    static boolean contactOrFallRisk(String pattern) {
        return pattern != null && CONTACT_OR_FALL_PATTERNS.contains(pattern);
    }

    /** Respuestas que se precargan desde el perfil común (equipo declarado en el Perfil 360). */
    static Map<String, Object> prefill(UserProfileData profile, int defaultExtra) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("f_extra", defaultExtra);
        List<String> eq = new ArrayList<>();
        if (profile.hasEquipment("balon")) eq.add("balon");
        if (profile.hasEquipment("conos")) eq.add("conos");
        if (!eq.isEmpty()) out.put("f_equipment", eq);
        return out;
    }

    /**
     * Material para sesiones propias. Si no lo contó (ni en el mundo ni en su perfil), suponemos balón: casi todo
     * futbolista tiene uno y sin él no hay técnica.
     */
    static List<String> equipment(Answers a, UserProfileData profile) {
        if (a.has("f_equipment")) return a.listWithoutNone("f_equipment");
        if (profile.hasEquipment("balon")) return List.of();
        return List.of("balon");
    }

    /** Explicación de cada valor por defecto usado porque la respuesta (opcional) no llegó. */
    static List<Factor> missingDefaults(Answers a, UserProfileData profile) {
        List<Factor> out = new ArrayList<>();
        if (!a.has("f_position")) {
            out.add(Factor.of("posicion", 0, "Aún no sabemos tu posición: trabajo general de jugador de campo "
                    + "(resistencia, velocidad y técnica equilibradas). Cuéntanosla para ajustarlo a tu puesto"));
        }
        if (!a.has("f_injury")) {
            out.add(Factor.of("lesion", 0, "No nos contaste lesiones recientes: usamos tu chequeo de salud y damos "
                    + "prioridad a la prevención (nórdicos, Copenhague y equilibrio)"));
        }
        if (!a.has("f_team_trainings")) {
            out.add(Factor.of("equipo", 0, "Suponemos que no entrenas con un equipo; si lo haces, cuéntanoslo para "
                    + "contar esa carga"));
        }
        if (!a.has("f_equipment") && !profile.hasEquipment("balon")) {
            out.add(Factor.of("material", 0, "Suponemos que tienes balón para la técnica; si no, dínoslo y lo quitamos"));
        }
        return out;
    }

    /** Avisos de intensidad al inscribirse: modo prudente, aval médico pendiente o impacto restringido. */
    static List<String> intensityNotices(HealthSummary h, boolean highImpactRestricted) {
        List<String> out = new ArrayList<>();
        if (h.prudentMode()) {
            out.add(h.moderateReason() + ". Mientras tanto: intensidad moderada, sin sprints repetidos ni saltos "
                    + "(los cambiamos por bicicleta, técnica y desplazamientos controlados).");
            return out;
        }
        if (!h.vigorousAllowed()) {
            out.add(h.moderateReason() + ": sin sprints repetidos.");
        }
        if (highImpactRestricted) {
            out.add("Por tu salud cambiamos sprints, saltos e intervalos de alto impacto por bicicleta, técnica y "
                    + "desplazamientos controlados.");
        }
        return out;
    }

    /** Razón de la restricción de impacto para la explicación del plan. */
    static String restrictionRationale(HealthSummary h) {
        if (h.prudentMode()) {
            return h.moderateReason() + ". Mientras tanto evitamos sprints, saltos e intervalos de alto impacto: "
                    + "los sustituimos por bicicleta, técnica y desplazamientos controlados";
        }
        return "Por tu salud evitamos sprints, saltos e intervalos de alto impacto: los sustituimos por bicicleta, "
                + "técnica y desplazamientos controlados";
    }

    static String avoidContactNotice() {
        return "Como tomas anticoagulantes, un golpe o una caída pueden causar sangrados: tu plan evita cabezazos, "
                + "saltos, caídas y duelos. Para jugar, prefiere formatos sin contacto (fútbol caminando, fútbol-tenis, "
                + "rondos o tiros a portería) y consulta con tu médico antes de competir.";
    }

    static String heartRateNotice() {
        return "Como tomas betabloqueadores, tu pulso no refleja el esfuerzo: guiamos la intensidad por esfuerzo "
                + "percibido (RPE 1–10) y la prueba del habla, no por pulsaciones.";
    }

    /** Avisos al inscribirse derivados del Perfil 360. */
    static List<String> profileNotices(ProfileInsights in) {
        List<String> out = new ArrayList<>();
        if (in.avoidContact()) out.add(avoidContactNotice());
        if (in.heartRateUnreliable()) out.add(heartRateNotice());
        return out;
    }

    /** Factores del plan derivados del Perfil 360. */
    static List<Factor> planFactors(ProfileInsights in) {
        List<Factor> out = new ArrayList<>();
        if (in.avoidContact()) {
            out.add(Factor.of("contacto", 0, "Como tomas anticoagulantes, quitamos cabezazos, saltos y caídas del "
                    + "portero; los duelos se hacen sin disputar el balón"));
        }
        if (in.heartRateUnreliable()) {
            out.add(Factor.of("intensidad_rpe", 0, "Como tomas betabloqueadores, la intensidad va por RPE y la prueba "
                    + "del habla, no por % de frecuencia cardiaca"));
        }
        return out;
    }

    /** Consejos del inicio derivados del Perfil 360 (citan el dato). */
    static List<Insight> profileTips(ProfileInsights in) {
        List<Insight> out = new ArrayList<>();
        if (in.avoidContact()) {
            out.add(Insight.of("🩸", "Fútbol sin contacto",
                    "Como tomas anticoagulantes: fútbol caminando, fútbol-tenis o rondos en lugar de partidos con choques.",
                    "warn"));
        }
        if (in.heartRateUnreliable()) {
            out.add(Insight.of("🫀", "Esfuerzo, no pulsaciones",
                    "Con betabloqueadores tu pulso no sube como el esfuerzo: guíate por el RPE y por si puedes hablar.",
                    "info"));
        }
        if (in.competitive()) {
            out.add(Insight.of("🏁", "Compite contigo",
                    "Te encanta competir: repite tus tests (sprint de 30 m, Cooper) cada 4 semanas e intenta batir tu marca.",
                    "info"));
        }
        if (in.prefersGroup()) {
            out.add(Insight.of("🤝", "Entrena acompañado",
                    "Prefieres entrenar con más gente: haz la prevención con compañeros y los pases o rondos en pareja.",
                    "info"));
        }
        return out;
    }
}
