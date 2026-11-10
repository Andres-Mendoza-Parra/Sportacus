package com.sportacus.backend.worlds.gym;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.catalog.CatalogModels.MovementInfo;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.core.UserProfileData;

/**
 * Reglas puras de personalización del Gimnasio a partir del Perfil 360 y del chequeo de salud. Cada ajuste se explica
 * al usuario citando el dato que lo motivó.
 */
final class GymPersonalization {

    private GymPersonalization() {
    }

    /** Lugar de entrenamiento deducido del perfil común (para precargar el onboarding); null si no hay datos. */
    static String defaultPlace(UserProfileData p) {
        if (p.hasEnvironment("gimnasio")) return "gimnasio";
        if (p.hasEquipment("mancuernas") || p.hasEquipment("kettlebell")) return "casa_mancuernas";
        if (p.hasEquipment("bandas")) return "casa_bandas";
        return null;
    }

    static boolean atHome(String place) {
        return place != null && place.startsWith("casa");
    }

    /** Nombre de la ruta para el usuario: sin hablar de grasa ni de déficit si hay señales de relación difícil con la comida. */
    static String trackLabel(String track, boolean eatingCaution) {
        return switch (track == null ? "base" : track) {
            case "hipertrofia" -> "ganar músculo";
            case "fuerza" -> "fuerza en los básicos";
            case "recomposicion" -> eatingCaution ? "fuerza y acondicionamiento" : "recomposición corporal";
            default -> "técnica y hábito";
        };
    }

    /** Texto de la opción de recomposición en el onboarding. */
    static String recompositionOption(boolean eatingCaution) {
        return eatingCaution ? "Ganar fuerza y condición con más ritmo" : "Perder grasa sin perder músculo";
    }

    /** Movimiento con saltos o pliometría (se evita en casa si el usuario no puede saltar). */
    static boolean jumpy(MovementInfo m) {
        if ("alto".equals(m.impact())) return true;
        String text = (m.key() + " " + m.name()).toLowerCase(Locale.ROOT);
        return text.contains("salto") || text.contains("saltos") || text.contains("jump") || text.contains("burpee")
                || text.contains("pliom");
    }

    /** ¿Hay que quitar los saltos del plan? Solo en casa y si dijo que no puede saltar. */
    static boolean avoidJumps(ProfileInsights in, String place) {
        return in.noJumpingAtHome() && atHome(place);
    }

    /** RIR del plan: 3 (lejos del fallo) mientras la intensidad vigorosa no esté habilitada. */
    static int rir(HealthSummary h, int trackRir) {
        return h.vigorousAllowed() ? trackRir : 3;
    }

    /** Avisos de inscripción derivados del perfil, la salud y el lugar. */
    static List<String> notices(HealthSummary h, ProfileInsights in, String place, boolean knowsLoads) {
        List<String> out = new ArrayList<>();
        if (!h.vigorousAllowed()) out.add(h.moderateReason() + ". Mientras tanto mantendremos RIR 3 (lejos del fallo).");
        if (!h.injuredZones().isEmpty()) {
            out.add("Excluimos los ejercicios que cargan tus zonas con molestias: " + String.join(", ", h.injuredZones()) + ".");
        }
        if (in.eatingCaution()) {
            out.add("Tu plan se centra en fuerza, energía y salud: no medimos ni perseguimos cambios de peso.");
        }
        if (avoidJumps(in, place)) {
            out.add("Como nos contaste que en casa no puedes saltar, tu plan no incluye saltos y el calentamiento es sin impacto.");
        }
        if (in.lowBudget() && atHome(place)) out.add(budgetTip());
        if (!knowsLoads) {
            out.add("Aún no sabemos tus cargas: en las primeras sesiones elige un peso con el que te sobren 2–3 repeticiones; "
                    + "con lo que registres, la app ajusta el resto.");
        }
        return out;
    }

    static String budgetTip() {
        return "Sin gastar de más: una mochila con libros o botellas de agua sirve de carga, y una silla firme reemplaza el banco.";
    }

    /** Indicación del calentamiento según si puede saltar o no. */
    static String warmupCue(boolean avoidJumps) {
        return avoidJumps ? "Sin saltos: marcha en el sitio. Después, 2 series de aproximación ligeras en el primer ejercicio"
                : "Después, 2 series de aproximación ligeras en el primer ejercicio";
    }
}
