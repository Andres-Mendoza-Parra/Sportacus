package com.sportacus.backend.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.profile.bank.BankModels.BankOption;
import com.sportacus.backend.profile.bank.BankModels.BankQuestion;
import com.sportacus.backend.profile.bank.QuestionBank;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.core.UserContext;
import com.sportacus.backend.world.core.UserProfileData;

/**
 * Convierte lo que se sabe del usuario en texto para el asistente: perfil común, salud, conclusiones del perfil derivado
 * (con instrucciones de cuidado) y las respuestas del Perfil 360 en lenguaje natural ("pregunta: respuesta"). Solo se
 * usa si el usuario activa "usar mis datos"; las áreas sensibles solo existen aquí si dio su consentimiento.
 */
@Component
public class ProfilePromptBuilder {

    private static final int MAX_FACTS = 45;
    private static final int MAX_CHARS = 5000;

    private final QuestionBank bank;

    public ProfilePromptBuilder(QuestionBank bank) {
        this.bank = bank;
    }

    public String build(UserContext u) {
        StringBuilder sb = new StringBuilder();
        UserProfileData p = u.profile();
        sb.append("- Nombre: ").append(u.displayName()).append('\n');
        if (p.age() != null) sb.append("- Edad: ").append(p.age()).append(" años\n");
        if (p.sex() != null && !"prefiero_no_decir".equals(p.sex())) sb.append("- Sexo: ").append(p.sex()).append('\n');
        boolean hideBody = u.insights().eatingCaution() || u.insights().facts().is("rc_ver_peso", "no");
        if (p.weightKg() != null && !hideBody) sb.append("- Peso: ").append(p.weightKg()).append(" kg\n");
        if (p.heightCm() != null) sb.append("- Estatura: ").append(p.heightCm()).append(" cm\n");
        if (p.daysPerWeek() != null) sb.append("- Días disponibles: ").append(p.daysPerWeek()).append(" por semana\n");
        if (p.minutesPerSession() != null) sb.append("- Tiempo por sesión: ").append(p.minutesPerSession()).append(" min\n");
        if (!p.environments().isEmpty()) sb.append("- Dónde entrena: ").append(String.join(", ", p.environments())).append('\n');
        if (!p.equipment().isEmpty()) sb.append("- Equipo: ").append(String.join(", ", p.equipment())).append('\n');

        HealthSummary h = u.health();
        if (!h.screened()) sb.append("- No ha hecho el chequeo de salud: recomienda intensidad moderada y sin saltos\n");
        if (!h.conditions().isEmpty()) {
            sb.append("- Condiciones/lesiones activas: ")
                    .append(String.join(", ", h.conditions().stream().map(HealthSummary.ConditionView::label).toList()))
                    .append('\n');
        }
        if (h.screened() && !h.vigorousAllowed()) sb.append("- Debe mantener intensidad moderada hasta tener aval médico\n");
        for (String rule : careRules(u)) sb.append("- ").append(rule).append('\n');

        List<String> facts = facts(u.insights(), hideBody);
        if (!facts.isEmpty()) {
            sb.append("- Lo que nos contó (Perfil 360):\n");
            for (String f : facts) sb.append("  · ").append(f).append('\n');
        }
        String out = sb.toString();
        return out.length() > MAX_CHARS ? out.substring(0, MAX_CHARS) + "…\n" : out;
    }

    /** Conclusiones accionables con instrucciones explícitas de cuidado para el modelo. */
    static List<String> careRules(UserContext u) {
        ProfileInsights in = u.insights();
        HealthSummary h = u.health();
        List<String> out = new ArrayList<>();
        if (in.readiness() != null) {
            out.add("Hoy: " + in.readiness().label()
                    + (in.readiness().reasons().isEmpty() ? "" : " (" + String.join(", ", in.readiness().reasons()) + ")"));
        }
        if (in.heartRateUnreliable()) out.add("Toma betabloqueadores: guía la intensidad por esfuerzo percibido (RPE) o la prueba del habla, nunca por pulsaciones");
        if (in.hypoglycemiaRisk()) out.add("Riesgo de hipoglucemia: recuerda comer antes, llevar carbohidratos y no entrenar en ayunas");
        if (in.avoidContact()) out.add("Toma anticoagulantes: evita recomendar contacto, choques o actividades con riesgo de caídas");
        if (in.eatingCaution()) out.add("IMPORTANTE: señales de relación difícil con la comida. No hables de déficit calórico, quemar calorías, perder peso ni dietas restrictivas; enfoca en salud, fuerza y bienestar, y si lo menciona, sugiere con calidez apoyo profesional");
        if (h.hasFlag("movilidad_reducida") || h.has("movilidad_reducida")) out.add("Movilidad reducida: propone variantes sentadas o con apoyo");
        if (h.hasFlag("baja_vision")) out.add("Baja visión: describe los ejercicios con referencias táctiles y auditivas, sin depender de imágenes");
        if (in.altitudeM() != null && in.altitudeM() >= 2000) out.add("Entrena a ~" + in.altitudeM() + " m de altitud: ritmos algo más lentos y aclimatación");
        if (in.wellbeingWho5() != null && in.wellbeingWho5() < 50) out.add("Bienestar bajo (WHO-5 " + in.wellbeingWho5() + "/100): tono especialmente cálido, metas pequeñas; si expresa malestar emocional intenso, sugiere buscar apoyo profesional (en Colombia, línea 192 opción 4)");
        if (in.tone() != null) out.add("Tono preferido: " + in.tone());
        if (in.learningFormat() != null) out.add("Prefiere aprender con: " + in.learningFormat());
        if (in.dropoutReason() != null && !"no_lo_deje".equals(in.dropoutReason())) out.add("La última vez dejó de entrenar por: " + in.dropoutReason() + " (anticípate a eso)");
        if (!in.barriers().isEmpty()) out.add("Barreras: " + String.join(", ", in.barriers()));
        if (in.dueEvent() != null) out.add("Evento con fecha: " + in.dueEvent() + (in.dueEventDate() == null ? "" : " el " + in.dueEventDate()));
        return out;
    }

    /** Respuestas del Perfil 360 en lenguaje natural, las de mayor prioridad primero (sin el check-in diario). */
    List<String> facts(ProfileInsights in, boolean hideBody) {
        Map<String, Object> values = in.facts().answers().values();
        List<BankQuestion> known = new ArrayList<>();
        for (String id : values.keySet()) {
            bank.question(id).filter(q -> !q.daily() && !id.startsWith("p_") && !(hideBody && id.startsWith("cu_"))).ifPresent(known::add);
        }
        known.sort(Comparator.comparingInt(BankQuestion::priorityOr3).reversed());
        List<String> out = new ArrayList<>();
        for (BankQuestion q : known) {
            if (out.size() >= MAX_FACTS) break;
            out.add(stripQuestion(q.prompt()) + ": " + render(q, values.get(q.id())));
        }
        return out;
    }

    private static String stripQuestion(String prompt) {
        return prompt.replace("¿", "").replace("?", "").trim();
    }

    private static String render(BankQuestion q, Object value) {
        if (q.options() == null || q.options().isEmpty()) {
            return String.valueOf(value) + (q.unit() == null ? "" : " " + q.unit());
        }
        List<String> labels = new ArrayList<>();
        List<?> values = value instanceof List<?> l ? l : List.of(String.valueOf(value));
        for (Object v : values) {
            String id = String.valueOf(v);
            labels.add(q.options().stream().filter(o -> o.id().equals(id)).map(BankOption::text).findFirst().orElse(id));
        }
        return String.join(", ", labels);
    }
}
