package com.sportacus.backend.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sportacus.backend.dashboard.dto.DashboardParts.InsightResponse;
import com.sportacus.backend.dashboard.dto.DashboardParts.MetricResponse;

/**
 * Reglas puras del dashboard (sin base de datos, fáciles de probar): qué gráfico se muestra según la meta,
 * qué consejos se generan y cómo se compone el score. Nada se inventa: cada regla lee un dato real del perfil
 * o de la rutina y, si el dato no existe, la regla simplemente no se activa.
 */
final class DashboardRules {

    static final String RADAR = "radar";
    static final String DONUT = "donut";
    static final String BARS = "bars";
    static final String GAUGE = "gauge";

    /** Tipos de gráfico que puede devolver {@link #focusType}. */
    static final Set<String> FOCUS_TYPES = Set.of(RADAR, DONUT, BARS, GAUGE);

    /** Referencia de actividad física semanal para adultos (OMS): 150 min de intensidad moderada. */
    static final int WEEKLY_MINUTES_TARGET = 150;

    /** Meta -> gráfico "natural" del panel. */
    private static final Set<String> MUSCLE_GOALS = Set.of("ganancia_muscular", "rendimiento_deportivo");
    private static final Set<String> WELLBEING_GOALS = Set.of(
            "bienestar_general", "flexibilidad", "gestion_estres", "rehabilitacion");

    /** Etiquetas de meta (mismo texto que la pantalla de rutina). */
    private static final Map<String, String> GOAL_LABELS = Map.of(
            "perdida_grasa", "Pérdida de grasa",
            "ganancia_muscular", "Ganancia muscular",
            "bienestar_general", "Bienestar general",
            "rehabilitacion", "Rehabilitación",
            "habito_activo", "Hábito activo",
            "rendimiento_deportivo", "Rendimiento deportivo",
            "flexibilidad", "Flexibilidad",
            "gestion_estres", "Gestión del estrés");

    private DashboardRules() {
    }

    static String goalLabel(String goalKey) {
        return goalKey == null ? null : GOAL_LABELS.getOrDefault(goalKey, "Bienestar general");
    }

    /**
     * Elige el tipo de gráfico principal.
     *
     * @param goal meta primaria (null = el usuario aún no la definió)
     * @param musclesWithSets cuántas de las 6 zonas del radar tienen series en la rutina vigente
     */
    static String focusType(String goal, int musclesWithSets) {
        if (goal == null) {
            return GAUGE;
        }
        if (MUSCLE_GOALS.contains(goal)) {
            // Un radar muscular con casi todo en cero no informa: cae a composición de la rutina.
            return musclesWithSets >= 2 ? RADAR : DONUT;
        }
        if (WELLBEING_GOALS.contains(goal)) {
            return RADAR;
        }
        return switch (goal) {
            case "perdida_grasa" -> DONUT;
            case "habito_activo" -> BARS;
            default -> GAUGE;
        };
    }

    // ───────────────────────── Score global ─────────────────────────

    static final int WEIGHT_STRENGTH = 40;
    static final int WEIGHT_AFFINITY = 40;
    static final int WEIGHT_CHECKLIST = 20;

    /** Promedio ponderado: fuerza del perfil 40 %, afinidad de la rutina 40 %, checklist 20 %. */
    static int globalScore(int strengthPct, int affinityPct, int checklistPct) {
        double score = (strengthPct * WEIGHT_STRENGTH + affinityPct * WEIGHT_AFFINITY
                + checklistPct * WEIGHT_CHECKLIST) / 100.0;
        return clamp((int) Math.round(score));
    }

    static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    static int pct(double part, double whole) {
        return whole <= 0 ? 0 : clamp((int) Math.round(part * 100.0 / whole));
    }

    // ───────────────────────── Puntajes de bienestar (0-100) ─────────────────────────

    /** Horas ideales 7-9; calidad 1-5. Devuelve null si no hay ningún dato. */
    static Integer sleepScore(Double hours, Integer quality) {
        List<Integer> parts = new ArrayList<>();
        if (hours != null) {
            double h = hours;
            double s = (h >= 7 && h <= 9) ? 100 : (h < 7 ? 100 - (7 - h) * 25 : 100 - (h - 9) * 20);
            parts.add(clamp((int) Math.round(s)));
        }
        if (quality != null) {
            parts.add(clamp(quality * 20));
        }
        return parts.isEmpty() ? null : (int) Math.round(parts.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    /** stress 1 (muy bajo) a 5 (muy alto) -> "calma" 100..0. */
    static Integer calmScore(Integer stress) {
        return stress == null ? null : clamp((int) Math.round((5 - stress) / 4.0 * 100));
    }

    /** Referencia de 2.5 L/día. */
    static Integer hydrationScore(Double liters) {
        return liters == null ? null : pct(liters, 2.5);
    }

    /** Pasos diarios (meta 8 000) o, si no hay, el tipo de actividad laboral. */
    static Integer dailyActivityScore(Integer steps, String workActivity) {
        if (steps != null && steps > 0) {
            return pct(steps, 8000);
        }
        if (workActivity == null) {
            return null;
        }
        return switch (workActivity) {
            case "sedentario" -> 15;
            case "leve" -> 35;
            case "moderado" -> 60;
            case "activo" -> 80;
            case "muy_activo" -> 100;
            default -> null;
        };
    }

    /** 3-5 comidas al día es lo habitual y regular. */
    static Integer nutritionScore(Integer mealsPerDay) {
        if (mealsPerDay == null) {
            return null;
        }
        if (mealsPerDay >= 3 && mealsPerDay <= 5) {
            return 100;
        }
        return mealsPerDay == 2 || mealsPerDay == 6 ? 70 : 40;
    }

    /** La movilidad ideal ronda el 25 % del tiempo de la rutina. */
    static int mobilityScore(int mobilitySharePct) {
        return pct(mobilitySharePct, 25);
    }

    // ───────────────────────── Indicadores ─────────────────────────

    static MetricResponse bmiMetric(Double bmi) {
        if (bmi == null) {
            return null;
        }
        String status;
        String hint;
        if (bmi < 18.5) {
            status = "low";
            hint = "Por debajo del rango saludable (18.5–24.9).";
        } else if (bmi < 25) {
            status = "good";
            hint = "Dentro del rango saludable (18.5–24.9).";
        } else if (bmi < 30) {
            status = "warn";
            hint = "Sobrepeso (25–29.9): la rutina combina fuerza y cardio.";
        } else {
            status = "warn";
            hint = "Obesidad (30 o más): prioriza ejercicios de bajo impacto.";
        }
        return new MetricResponse("bmi", "IMC", String.format(java.util.Locale.US, "%.1f", bmi), "kg/m²", status, hint,
                clamp((int) Math.round(Math.min(bmi, 35) / 35.0 * 100)));
    }

    static MetricResponse sleepMetric(Double hours) {
        if (hours == null) {
            return null;
        }
        String status = hours >= 7 && hours <= 9 ? "good" : (hours >= 6 && hours < 7 ? "warn" : (hours > 9 ? "warn" : "low"));
        String hint = switch (status) {
            case "good" -> "Dentro de las 7–9 h recomendadas.";
            case "low" -> "Duermes poco: la recuperación muscular se resiente.";
            default -> hours > 9 ? "Más de 9 h: revisa la calidad del sueño." : "Un poco por debajo de las 7 h ideales.";
        };
        return new MetricResponse("sleep", "Sueño", trim(hours), "h/noche", status, hint, pct(hours, 9));
    }

    static MetricResponse waterMetric(Double liters) {
        if (liters == null) {
            return null;
        }
        String status = liters >= 2 ? "good" : (liters >= 1.5 ? "warn" : "low");
        String hint = switch (status) {
            case "good" -> "Buena hidratación (referencia: 2–2.5 L).";
            case "warn" -> "Casi: apunta a 2 L al día.";
            default -> "Bebe más agua: afecta rendimiento y recuperación.";
        };
        return new MetricResponse("water", "Hidratación", trim(liters), "L/día", status, hint, pct(liters, 2.5));
    }

    static MetricResponse daysMetric(Integer days) {
        if (days == null) {
            return null;
        }
        String status = days >= 3 ? "good" : (days == 2 ? "warn" : "low");
        String hint = days >= 3 ? "Frecuencia suficiente para progresar." : "Con 3 o más días el progreso es más estable.";
        return new MetricResponse("days", "Días de entrenamiento", String.valueOf(days), "días/sem", status, hint,
                pct(days, 5));
    }

    /** Etiquetas de tiempo por sesión -> (texto, minutos representativos). */
    static MetricResponse sessionTimeMetric(String timePerSession) {
        if (timePerSession == null) {
            return null;
        }
        String text;
        int rep;
        switch (timePerSession) {
            case "menos_30" -> { text = "< 30"; rep = 25; }
            case "30_45" -> { text = "30–45"; rep = 40; }
            case "45_60" -> { text = "45–60"; rep = 55; }
            case "60_90" -> { text = "60–90"; rep = 75; }
            case "mas_90" -> { text = "> 90"; rep = 100; }
            default -> { return null; }
        }
        String status = rep >= 40 ? "good" : "warn";
        String hint = rep >= 40 ? "Tiempo suficiente para una sesión completa." : "Sesiones cortas: la rutina será más compacta.";
        return new MetricResponse("session", "Tiempo por sesión", text, "min", status, hint, pct(rep, 60));
    }

    static String hoursText(double hours) {
        return trim(hours) + " h";
    }

    static String litersText(double liters) {
        return trim(liters) + " L";
    }

    private static String trim(double value) {
        return value == Math.rint(value)
                ? String.valueOf((int) value) : String.format(java.util.Locale.US, "%.1f", value);
    }

    // ───────────────────────── Consejos ─────────────────────────

    /** Datos reales que alimentan las reglas (null = no se conoce). */
    record InsightInput(
            boolean activeInjury, Double sleepHours, Integer stress, Double waterLiters, Double bmi,
            int strengthPct, boolean hasRoutine, int doneCount, int totalCount) {
    }

    private static final int MAX_INSIGHTS = 4;

    /** Hasta 4 consejos accionables por prioridad (seguridad > hábitos > perfil > rutina). */
    static List<InsightResponse> insights(InsightInput in) {
        List<InsightResponse> all = new ArrayList<>();

        if (in.activeInjury()) {
            all.add(new InsightResponse("🩹", "Lesión activa registrada",
                    "Tu rutina evita las zonas afectadas. Si el dolor aumenta, detén el ejercicio y consulta a un profesional.",
                    "warn", "/questionnaires"));
        }
        if (in.sleepHours() != null && in.sleepHours() < 6.5) {
            all.add(new InsightResponse("😴", "Duermes poco",
                    "Con " + trim(in.sleepHours()) + " h por noche la recuperación se resiente. Intenta acercarte a 7–9 h.",
                    "warn", "/questionnaires"));
        }
        if (in.stress() != null && in.stress() >= 4) {
            all.add(new InsightResponse("🧘", "Estrés alto",
                    "Añade movilidad, respiración o caminatas suaves; entrenar al límite con mucho estrés rinde menos.",
                    "warn", "/routine"));
        }
        if (in.waterLiters() != null && in.waterLiters() < 1.5) {
            all.add(new InsightResponse("💧", "Hidratación baja",
                    "Bebes " + trim(in.waterLiters()) + " L al día. Apunta a 2 L y súmale agua en los días de entrenamiento.",
                    "warn", "/questionnaires"));
        }
        if (in.strengthPct() < 50) {
            all.add(new InsightResponse("🎯", "Afina tu perfil",
                    "Tu perfil está al " + in.strengthPct() + " %. Cada plantilla que completes hace más precisa tu rutina.",
                    "info", "/questionnaires"));
        }
        if (in.hasRoutine() && in.totalCount() > 0) {
            if (in.doneCount() == 0) {
                all.add(new InsightResponse("🏋️", "Tu rutina te espera",
                        "Tienes " + in.totalCount() + " ejercicios pendientes en el checklist. Marca el primero cuando lo hagas.",
                        "info", "/routine"));
            } else if (in.doneCount() >= in.totalCount()) {
                all.add(new InsightResponse("🏆", "¡Rutina completada!",
                        "Marcaste todos los ejercicios. Regenera la rutina para recibir una nueva.",
                        "good", "/routine"));
            } else {
                all.add(new InsightResponse("🔥", "Buen ritmo",
                        "Llevas " + in.doneCount() + " de " + in.totalCount() + " ejercicios del checklist.",
                        "good", "/routine"));
            }
        }
        if (in.bmi() != null && (in.bmi() < 18.5 || in.bmi() >= 30)) {
            all.add(new InsightResponse("⚖️", "Revisa tu IMC",
                    "Tu IMC es " + String.format(java.util.Locale.US, "%.1f", in.bmi())
                            + ". La rutina ya lo considera; un profesional puede orientarte mejor.",
                    "info", "/profile"));
        }

        // Sin riesgos detectados y con datos suficientes, se reconoce lo que va bien.
        if (all.isEmpty()) {
            all.add(new InsightResponse("✅", "Todo en orden",
                    "No detectamos alertas con los datos que tienes. Sigue completando tu perfil para más recomendaciones.",
                    "good", "/questionnaires"));
        }
        return all.size() > MAX_INSIGHTS ? new ArrayList<>(all.subList(0, MAX_INSIGHTS)) : all;
    }
}
