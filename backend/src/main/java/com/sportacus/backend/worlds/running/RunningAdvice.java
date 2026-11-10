package com.sportacus.backend.worlds.running;

import java.util.ArrayList;
import java.util.List;

import com.sportacus.backend.world.core.Factor;
import com.sportacus.backend.world.core.GeneratedPlan;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedSession;
import com.sportacus.backend.world.core.ProfileFacts;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.core.Widget.Insight;

/**
 * Ajustes del plan de running a partir del Perfil 360 ({@link ProfileInsights}), puros y probados en
 * RunningAdviceTest. Cada ajuste se explica citando el dato que lo motiva. La disposición del día (check-in) NO se
 * aplica aquí: la aplica el núcleo al mostrar el plan.
 */
final class RunningAdvice {

    private RunningAdvice() {
    }

    /** Te_dispositivos (Perfil 360) → r_watch del onboarding, solo cuando la correspondencia es directa. */
    static String watchFromDevices(List<String> devices) {
        if (devices == null || devices.isEmpty()) return null;
        if (devices.contains("reloj_gps")) return "gps";
        if (devices.contains("solo_celular")) return "movil";
        return null;
    }

    /** Cómo mide sus salidas: lo que respondió en el mundo; si no, lo que dijo en el Perfil 360; si no, "nada". */
    static String watch(String worldAnswer, ProfileFacts facts) {
        if (worldAnswer != null) return worldAnswer;
        String fromProfile = watchFromDevices(facts == null ? null : facts.list("te_dispositivos"));
        return fromProfile == null ? "nada" : fromProfile;
    }

    /** "~2500 m" o null. */
    static String altitudeLabel(Integer altitudeM) {
        return altitudeM == null ? null : "~" + altitudeM + " m";
    }

    /** Porcentaje legible del factor de altitud: 1,04 → 4. */
    static int altitudePct(double factor) {
        return (int) Math.round((factor - 1) * 100);
    }

    /** Mejor franja horaria según calor y seguridad nocturna (null si no aplica nada). */
    static String bestHours(ProfileInsights in) {
        if (in.heatHumid() && in.unsafeAtNight()) {
            return "Sal temprano en la mañana: evitas el calor y la oscuridad. Si solo puedes de noche, usa cinta o un circuito iluminado y concurrido";
        }
        if (in.heatHumid()) {
            return "Sal temprano (antes de las 9) o al final de la tarde, cuando baja el calor";
        }
        if (in.unsafeAtNight()) {
            return "Programa tus salidas al aire libre con luz de día; si solo puedes de noche, usa cinta o un lugar iluminado y concurrido";
        }
        return null;
    }

    /**
     * Razones del plan que vienen del perfil.
     *
     * @param pacesScaled   se aplicó el factor de altitud a los ritmos (marca declarada, no medida allí)
     * @param measuredHere  los ritmos salen de un test o salida registrada donde entrena
     * @param hasPaces      hay ritmos calculados
     */
    static List<Factor> rationale(ProfileInsights in, boolean pacesScaled, boolean measuredHere, boolean hasPaces) {
        List<Factor> out = new ArrayList<>();
        double f = in.altitudePaceFactor();
        if (f > 1.0) {
            String where = "Como entrenas a " + altitudeLabel(in.altitudeM());
            String text;
            if (hasPaces && pacesScaled) {
                text = where + ", tus ritmos objetivo son un " + altitudePct(f) + " % más lentos que a nivel del mar: hay menos oxígeno";
            } else if (hasPaces && measuredHere) {
                text = where + ", tus ritmos salen de un esfuerzo medido allí y ya incluyen el efecto de la altura";
            } else {
                text = where + ", el mismo esfuerzo te dará un ritmo un " + altitudePct(f) + " % más lento: guíate por sensaciones";
            }
            out.add(Factor.of("altitud", 0, text + ". Si acabas de llegar, las 2 primeras semanas ve aún más suave y bebe más agua"));
        }
        if (in.heartRateUnreliable()) {
            out.add(Factor.of("intensidad_rpe", 0, "Según tu chequeo tomas betabloqueadores: tu pulso no refleja el esfuerzo, así que "
                    + "guiamos la intensidad por esfuerzo percibido (RPE) y la prueba del habla, nunca por pulsaciones"));
        }
        String hours = bestHours(in);
        if (hours != null) {
            String why = in.heatHumid() && in.unsafeAtNight() ? "Como nos contaste que tu clima es cálido y tu zona es insegura de noche"
                    : in.heatHumid() ? "Como nos contaste que tu clima es cálido" : "Como nos contaste que tu zona es insegura de noche";
            out.add(Factor.of("horario", 0, why + ": " + Character.toLowerCase(hours.charAt(0)) + hours.substring(1)));
        }
        if (in.heatHumid()) {
            out.add(Factor.of("hidratacion", 0, "Con calor, bebe antes de salir, a sorbos durante si pasas de 45 minutos y repón después"));
        }
        if (in.hypoglycemiaRisk()) {
            out.add(Factor.of("glucosa", 0, "Según tu chequeo usas insulina: no corras en ayunas, come algo 1–2 h antes y lleva "
                    + "carbohidratos rápidos en las tiradas largas"));
        }
        return out;
    }

    /** Nota adicional para una sesión del plan (null si no hay nada que añadir). */
    static String sessionNote(GeneratedSession s, int programWeek, ProfileInsights in) {
        List<String> parts = new ArrayList<>();
        if (in.altitudePaceFactor() > 1.0 && programWeek < 2 && s.dayIndex() == 0) {
            parts.add("Altura (" + altitudeLabel(in.altitudeM()) + "): si llevas poco tiempo ahí, ve más suave de lo normal y bebe más.");
        }
        boolean longRun = "tirada_larga".equals(s.kind()) || "prueba".equals(s.kind());
        if (longRun && in.hypoglycemiaRisk()) {
            parts.add("Come algo 1–2 h antes y lleva carbohidratos rápidos (gel, dulces); toma algo cada 30–45 min y, si tu médico lo indicó, mide tu glucosa.");
        }
        if (longRun && (in.heatHumid() || in.unsafeAtNight())) {
            parts.add(in.heatHumid() && !in.unsafeAtNight() ? "Con calor: sal temprano o al final de la tarde y lleva agua."
                    : in.heatHumid() ? "Con calor: sal temprano en la mañana y lleva agua."
                            : "Sal con luz de día.");
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    /** Añade al plan las razones y notas del perfil. {@code startWeek}: semana de programa del índice 0. */
    static GeneratedPlan apply(GeneratedPlan plan, ProfileInsights in, int startWeek, boolean pacesScaled,
            boolean measuredHere, boolean hasPaces) {
        List<Factor> why = new ArrayList<>(plan.rationale());
        why.addAll(rationale(in, pacesScaled, measuredHere, hasPaces));
        List<GeneratedSession> sessions = new ArrayList<>();
        for (GeneratedSession s : plan.sessions()) {
            String extra = sessionNote(s, startWeek + s.weekIndex(), in);
            if (extra == null) {
                sessions.add(s);
            } else {
                String notes = s.notes() == null ? extra : s.notes() + ". " + extra;
                sessions.add(new GeneratedSession(s.weekIndex(), s.dayIndex(), s.weekday(), s.title(), s.focus(), s.kind(),
                        s.estMinutes(), notes, s.blocks()));
            }
        }
        return new GeneratedPlan(plan.title(), plan.summary(), plan.weeks(), plan.score(), why, sessions);
    }

    /** Tarjetas "Para ti" derivadas del perfil. */
    static List<Insight> insights(ProfileInsights in) {
        List<Insight> out = new ArrayList<>();
        if (in.heartRateUnreliable()) {
            out.add(Insight.of("🗣️", "Tu guía es el esfuerzo, no el pulso",
                    "Con betabloqueadores las pulsaciones no reflejan el esfuerzo: suave = puedes conversar (RPE 3–4); fuerte = frases cortas (RPE 7–8).", "info"));
        }
        if (in.altitudePaceFactor() > 1.0) {
            out.add(Insight.of("⛰️", "Entrenas en altura",
                    "A " + altitudeLabel(in.altitudeM()) + " el mismo esfuerzo da un ritmo un " + altitudePct(in.altitudePaceFactor())
                            + " % más lento. Es normal: tus ritmos objetivo ya lo tienen en cuenta.", "info"));
        }
        String hours = bestHours(in);
        if (hours != null) out.add(Insight.of(in.heatHumid() ? "☀️" : "🌙", "Tu mejor horario", hours + ".", "info"));
        if (in.hypoglycemiaRisk()) {
            out.add(Insight.of("🍌", "Nunca en ayunas", "Come algo antes de correr y lleva carbohidratos rápidos en las salidas largas.", "warn"));
        }
        return out;
    }

    /** Líneas extra para el asistente. */
    static String persona(ProfileInsights in) {
        StringBuilder sb = new StringBuilder();
        if (in.heartRateUnreliable()) sb.append(" Toma betabloqueadores: NUNCA uses zonas de frecuencia cardiaca; guía por RPE y la prueba del habla.");
        if (in.altitudePaceFactor() > 1.0) sb.append(" Entrena a ").append(altitudeLabel(in.altitudeM()))
                .append(": ritmos un ").append(altitudePct(in.altitudePaceFactor())).append(" % más lentos que a nivel del mar.");
        if (in.heatHumid()) sb.append(" Clima cálido: horarios frescos e hidratación.");
        if (in.unsafeAtNight()) sb.append(" Su zona es insegura de noche: no sugieras salidas exteriores nocturnas.");
        if (in.hypoglycemiaRisk()) sb.append(" Usa insulina: nada de correr en ayunas; carbohidratos en salidas largas.");
        return sb.toString();
    }
}
