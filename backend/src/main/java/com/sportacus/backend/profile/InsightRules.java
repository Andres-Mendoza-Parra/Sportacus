package com.sportacus.backend.profile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.core.ProfileFacts;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.core.ProfileInsights.Readiness;

/**
 * Reglas puras que convierten respuestas en conclusiones accionables (docs/19 §5). Dependen de los ids "ancla" del
 * banco de preguntas; si un dato falta, el resultado es neutro.
 */
public final class InsightRules {

    private static final Set<String> OFTEN = Set.of("a_menudo", "siempre");

    private InsightRules() {
    }

    public static ProfileInsights derive(ProfileFacts f, HealthSummary h, LocalDate today) {
        Integer who5 = who5(f);
        String event = f.string("me_evento");
        boolean hasEvent = event != null && !event.equals("ninguno");
        String mode = f.string("tr_transporte");
        Double commute = f.number("tr_transporte_min");
        Integer commuteWeek = (("caminando".equals(mode) || "bici".equals(mode)) && commute != null)
                ? (int) Math.round(commute * 2 * 5) : null;
        return new ProfileInsights(
                f,
                readiness(f, today),
                altitude(f.string("ub_altitud")),
                h.hasFlag("fc_no_fiable"),
                h.hasFlag("riesgo_hipoglucemia"),
                h.hasFlag("anticoagulado") || h.has("anticoagulantes"),
                eatingCaution(f),
                f.is("es_saltar_casa", "no"),
                f.is("ec_presupuesto", "cero") || f.is("ec_presupuesto", "bajo"),
                f.is("ub_clima", "calido_humedo") || f.is("ub_clima", "calido_seco"),
                f.is("ub_seguridad_noche", "inseguro"),
                f.contains("so_mascota", "perro"),
                f.is("so_hijos", "si_pequenos"),
                f.is("mo_social", "acompanado") || f.is("mo_social", "grupo"),
                f.is("mo_competir", "me_encanta"),
                f.is("tr_carga", "pesado"),
                f.number("ha_horas_sentado") == null ? null : (int) Math.round(f.number("ha_horas_sentado")),
                commuteWeek,
                f.string("ap_tono"),
                f.string("ap_formato"),
                f.answers().listWithoutNone("ba_barreras"),
                f.string("hi_abandono"),
                who5,
                f.string("al_dieta"),
                hasEvent ? event : null,
                hasEvent ? f.string("me_fecha") : null);
    }

    /** WHO-5: suma de 5 ítems 0–5 × 4 = 0–100. Solo con los 5 ítems respondidos. */
    static Integer who5(ProfileFacts f) {
        int sum = 0;
        for (int i = 1; i <= 5; i++) {
            Double v = f.number("bi_who5_" + i);
            if (v == null) return null;
            sum += (int) Math.round(v);
        }
        return sum * 4;
    }

    static Integer altitude(String band) {
        if (band == null) return null;
        return switch (band) {
            case "bajo_1000" -> 500;
            case "1000_2000" -> 1500;
            case "2000_3000" -> 2500;
            case "mas_3000" -> 3200;
            default -> null;
        };
    }

    static boolean eatingCaution(ProfileFacts f) {
        int often = 0;
        for (String id : List.of("rc_preocupacion", "rc_control", "rc_restriccion")) {
            String v = f.string(id);
            if (v != null && OFTEN.contains(v)) often++;
        }
        String compensate = f.string("rc_compensar");
        boolean compensates = compensate != null && !compensate.equals("nunca");
        return compensates || often >= 1;
    }

    /**
     * Disposición del día a partir del check-in de HOY. Resta por sueño corto o malo, energía baja, estrés alto y dolor;
     * nunca baja de 0,6 (una sesión suave siempre es posible) y con dolor ≥ 7 sugiere descanso activo.
     */
    static Readiness readiness(ProfileFacts f, LocalDate today) {
        if (!f.answeredOn("ck_energia", today) && !f.answeredOn("ck_sueno_horas", today)) return null;
        double factor = 1.0;
        List<String> reasons = new ArrayList<>();
        Double sleep = today(f, "ck_sueno_horas", today);
        Double quality = today(f, "ck_sueno_calidad", today);
        Double energy = today(f, "ck_energia", today);
        Double stress = today(f, "ck_estres", today);
        Double pain = today(f, "ck_dolor", today);
        if (sleep != null && sleep < 6) {
            factor -= sleep < 5 ? 0.2 : 0.1;
            reasons.add("Dormiste " + Math.round(sleep) + " h");
        }
        if (quality != null && quality <= 2) {
            factor -= 0.1;
            reasons.add("Dormiste mal");
        }
        if (energy != null && energy <= 2) {
            factor -= 0.1;
            reasons.add("Tienes poca energía");
        }
        if (stress != null && stress >= 4) {
            factor -= 0.05;
            reasons.add("Estrés alto");
        }
        if (pain != null && pain >= 5) {
            factor -= pain >= 7 ? 0.25 : 0.15;
            reasons.add("Dolor " + Math.round(pain) + "/10");
        }
        factor = Math.max(0.6, Math.round(factor * 100) / 100.0);
        String label;
        if (pain != null && pain >= 7) label = "Hoy, descanso activo: movilidad suave y caminar";
        else if (factor >= 0.95) label = "Buen día para entrenar";
        else if (factor >= 0.8) label = "Hoy, un poco más suave (−" + Math.round((1 - factor) * 100) + " % de volumen)";
        else label = "Hoy, sesión suave (−" + Math.round((1 - factor) * 100) + " % de volumen, lejos del fallo)";
        return new Readiness(factor, label, reasons);
    }

    private static Double today(ProfileFacts f, String id, LocalDate today) {
        return f.answeredOn(id, today) ? f.number(id) : null;
    }
}
