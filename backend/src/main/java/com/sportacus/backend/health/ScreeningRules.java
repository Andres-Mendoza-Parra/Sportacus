package com.sportacus.backend.health;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.sportacus.backend.common.Answers;

/**
 * Reglas puras del chequeo (sin BD, fáciles de probar). Simplificación del algoritmo ACSM 2015:
 * <ul>
 * <li>Síntomas cardiovasculares o banderas rojas → riesgo alto y aval médico obligatorio.</li>
 * <li>Enfermedad cardiovascular, metabólica o renal diagnosticada + inactivo → aval médico antes de empezar.</li>
 * <li>Esa misma enfermedad + activo → puede seguir a intensidad moderada; la vigorosa requiere aval.</li>
 * <li>Cirugía reciente sin autorización → aval obligatorio. Embarazo → riesgo moderado y ajustes.</li>
 * </ul>
 */
public final class ScreeningRules {

    private static final Set<String> CVMR = Set.of("cardiopatia", "hipertension", "diabetes", "renal");

    private ScreeningRules() {
    }

    public record Result(String riskLevel, List<String> flags, boolean requiresClearance, boolean redFlags) {
    }

    public static Result evaluate(Answers a) {
        List<String> flags = new ArrayList<>();
        boolean clearance = false;
        int risk = 0; // 0 bajo, 1 moderado, 2 alto

        boolean active = a.yes("s_active");
        List<String> diagnosis = a.listWithoutNone("s_diagnosis");
        List<String> symptoms = a.listWithoutNone("s_symptoms");
        List<String> redFlags = a.listWithoutNone("s_red_flags");

        if (!symptoms.isEmpty()) {
            flags.add("cardio_sintomas");
            clearance = true;
            risk = 2;
        }
        if (!redFlags.isEmpty()) {
            flags.add("bandera_roja");
            redFlags.forEach(f -> flags.add("bandera_roja_" + f));
            clearance = true;
            risk = 2;
        }
        boolean cvmr = diagnosis.stream().anyMatch(CVMR::contains);
        if (cvmr) {
            flags.add("enfermedad_cvmr");
            if (!active) {
                clearance = true;
                flags.add("cvmr_inactivo");
            } else {
                flags.add("vigorosa_requiere_aval");
            }
            risk = Math.max(risk, 1);
        }
        if (!active) {
            flags.add("inactivo");
        }
        if (a.yes("s_surgery")) {
            flags.add("cirugia_reciente");
            if (!a.yes("s_clearance")) {
                clearance = true;
                flags.add("cirugia_sin_aval");
            }
            risk = Math.max(risk, 1);
        }
        if (a.yes("s_pregnancy")) {
            flags.add("embarazo");
            risk = Math.max(risk, 1);
        }
        if ("grave".equals(a.string("s_pain_severity")) && !a.listWithoutNone("s_pain_zones").isEmpty()) {
            flags.add("dolor_limitante");
            risk = Math.max(risk, 1);
        }
        String level = switch (risk) {
            case 2 -> "alto";
            case 1 -> "moderado";
            default -> "bajo";
        };
        return new Result(level, flags, clearance, !redFlags.isEmpty());
    }
}
