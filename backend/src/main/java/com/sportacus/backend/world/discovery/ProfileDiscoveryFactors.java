package com.sportacus.backend.world.discovery;

import java.util.ArrayList;
import java.util.List;

import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.core.Factor;
import com.sportacus.backend.world.core.ProfileFacts;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.core.UserContext;

/**
 * Afinidad por mundo a partir del Perfil 360 (lo que el usuario contó a su ritmo), común a todos los mundos. Se suma a
 * los factores propios de cada módulo. Cada factor cita el dato que lo motiva.
 */
public final class ProfileDiscoveryFactors {

    private ProfileDiscoveryFactors() {
    }

    public static List<Factor> of(String world, UserContext user) {
        ProfileInsights in = user.insights();
        ProfileFacts f = in.facts();
        HealthSummary h = user.health();
        List<Factor> out = new ArrayList<>();
        boolean reducedMobility = h.has("movilidad_reducida") || h.hasFlag("movilidad_reducida");

        switch (world) {
            case "gym" -> {
                if (in.lowBudget()) out.add(Factor.of("p360_presupuesto", -10,
                        "Tu presupuesto es ajustado y el gimnasio suele tener cuota mensual"));
                if (f.contains("p_environments", "gimnasio")) out.add(Factor.of("p360_gym_acceso", 10,
                        "Tienes acceso a un gimnasio"));
                if (in.youngChildren()) out.add(Factor.of("p360_hijos", -5,
                        "Con hijos pequeños, ir al gimnasio puede ser difícil de encajar"));
            }
            case "calisthenics" -> {
                if (in.lowBudget()) out.add(Factor.of("p360_presupuesto", 8, "Se entrena casi sin equipo: encaja con tu presupuesto"));
                if (in.youngChildren()) out.add(Factor.of("p360_hijos", 5, "Puedes entrenar en casa en ratos cortos"));
                if (reducedMobility) out.add(Factor.of("p360_movilidad", -20, "Exige cargar tu peso corporal en el suelo y en barras"));
            }
            case "running" -> {
                if (in.lowBudget()) out.add(Factor.of("p360_presupuesto", 6, "Solo necesitas unos tenis"));
                if (in.hasDog()) out.add(Factor.of("p360_perro", 6, "Tu perro puede acompañarte en las salidas suaves"));
                if (in.competitive()) out.add(Factor.of("p360_competir", 5, "Las carreras populares dan metas con fecha"));
                if (in.unsafeAtNight() && f.is("ag_horario", "noche")) out.add(Factor.of("p360_noche", -8,
                        "Entrenas de noche y tu entorno no es seguro a esa hora"));
                if (reducedMobility) out.add(Factor.of("p360_movilidad", -30, "Correr no encaja con tu movilidad actual"));
                if ("carrera".equals(in.dueEvent())) out.add(Factor.of("p360_evento",
                        15, "Tienes una carrera en el calendario"));
            }
            case "football" -> {
                if (in.prefersGroup()) out.add(Factor.of("p360_grupo", 8, "Prefieres entrenar acompañado"));
                if (in.competitive()) out.add(Factor.of("p360_competir", 8, "Te motiva competir"));
                if (in.avoidContact()) out.add(Factor.of("p360_contacto", -25,
                        "Tomas anticoagulantes: los deportes de contacto tienen riesgo de golpes y caídas"));
                if (reducedMobility) out.add(Factor.of("p360_movilidad", -30, "El fútbol exige carrera y cambios de dirección"));
            }
            case "health" -> {
                if (reducedMobility) out.add(Factor.of("p360_movilidad", 15, "Tiene variantes sentadas y con apoyo"));
                if (in.physicalJob()) out.add(Factor.of("p360_trabajo", 5, "Tu trabajo ya es físico: movilidad y recuperación te ayudan"));
                if (in.sittingHours() != null && in.sittingHours() >= 8) out.add(Factor.of("p360_sentado", 6,
                        "Pasas " + in.sittingHours() + " h sentado: pausas activas y movilidad"));
                if (in.wellbeingWho5() != null && in.wellbeingWho5() < 50) out.add(Factor.of("p360_bienestar", 5,
                        "Un programa suave y constante ayuda al bienestar"));
            }
            default -> {
                // mundos nuevos: sin factores de perfil todavía
            }
        }
        if (in.dropoutReason() != null) {
            switch (in.dropoutReason()) {
                case "aburrimiento" -> {
                    if (world.equals("calisthenics") || world.equals("football")) out.add(Factor.of("p360_abandono", 5,
                            "La última vez lo dejaste por aburrimiento: aquí hay retos y variedad"));
                }
                case "lesion" -> {
                    if (world.equals("health")) out.add(Factor.of("p360_abandono", 8,
                            "La última vez lo dejaste por una lesión: empezamos cuidándote"));
                }
                case "tiempo" -> {
                    if (world.equals("calisthenics") || world.equals("running")) out.add(Factor.of("p360_abandono", 4,
                            "La última vez lo dejaste por falta de tiempo: sesiones cortas y sin desplazarte"));
                }
                default -> {
                    // sin ajuste
                }
            }
        }
        return out;
    }
}
