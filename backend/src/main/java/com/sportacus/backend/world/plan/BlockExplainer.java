package com.sportacus.backend.world.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.catalog.CatalogModels.MovementInfo;
import com.sportacus.backend.world.catalog.CatalogModels.ProgressionInfo;
import com.sportacus.backend.world.catalog.CatalogModels.StepInfo;
import com.sportacus.backend.world.catalog.CatalogModels.WorldMovement;
import com.sportacus.backend.world.catalog.CatalogRepository;
import com.sportacus.backend.world.catalog.SafetyFilter;
import com.sportacus.backend.world.core.PlanView.BlockView;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.core.WorldContext;
import com.sportacus.backend.world.core.WorldRegistry;

/**
 * Explica cada bloque del plan (docs/20): POR QUÉ está en tu plan, CÓMO dosificarlo y QUÉ tener en cuenta, con los
 * datos del usuario. Combina el papel del bloque en la sesión, las notas del mundo, la progresión, la ruta, el equipo,
 * la salud y el check-in de hoy. Los mundos pueden añadir razones de nicho con {@code WorldModule.explainBlock}.
 */
@Component
public class BlockExplainer {

    private final CatalogRepository catalog;
    private final SafetyFilter safety;
    private final WorldRegistry registry;

    public BlockExplainer(CatalogRepository catalog, SafetyFilter safety, WorldRegistry registry) {
        this.catalog = catalog;
        this.safety = safety;
        this.registry = registry;
    }

    /** Razón para el usuario: icono, título, texto y tono (info | success | warn | danger). */
    public record Reason(String icon, String title, String text, String tone) {
    }

    public record ProgressionStatus(String key, String name, int step, int total, String stepName, String nextStepName,
            String unlock) {
    }

    public record Explanation(List<Reason> why, List<String> dose, List<Reason> considerations,
            ProgressionStatus progression) {
    }

    /** Razón principal en una línea (tarjeta del bloque en la vista previa y el reproductor). */
    public String summary(WorldContext ctx, String blockType, String movementKey, String notes, String progressionKey,
            Integer stepIndex) {
        if (progressionKey != null && stepIndex != null) {
            ProgressionStatus ps = progression(ctx, progressionKey, stepIndex);
            if (ps != null) return "Tu escalón " + ps.step() + " de " + ps.total() + " en " + ps.name();
        }
        if (notes != null && !notes.isBlank()) return firstSentence(notes);
        return role(blockType, movementKey == null ? null : catalog.movement(movementKey).orElse(null), null).text();
    }

    public Explanation explain(WorldContext ctx, BlockView b, String sessionFocus) {
        MovementInfo m = b.movementKey() == null ? null : catalog.movement(b.movementKey()).orElse(null);
        WorldMovement wm = b.movementKey() == null ? null
                : catalog.worldMovement(ctx.world().worldKey(), b.movementKey()).orElse(null);
        ProfileInsights in = ctx.user().insights();
        HealthSummary h = ctx.user().health();
        ProgressionStatus ps = b.progressionKey() == null || b.stepIndex() == null ? null
                : progression(ctx, b.progressionKey(), b.stepIndex());

        // ── Por qué ──
        List<Reason> why = new ArrayList<>();
        if (ps != null) {
            why.add(new Reason("🪜", "Tu escalón actual",
                    "Es el escalón " + ps.step() + " de " + ps.total() + " de " + ps.name() + ": justo tu nivel, ni más "
                            + "fácil ni más difícil." + (ps.nextStepName() == null ? " Es el último del árbol."
                                    : " Siguiente: " + ps.nextStepName() + "."),
                    "success"));
        }
        if (b.notes() != null && !b.notes().isBlank() && (ps == null || !b.notes().startsWith(ps.stepName()))) {
            why.add(new Reason("🎯", "Para tu plan", b.notes().trim(), "info"));
        }
        why.add(role(b.blockType(), m, sessionFocus));
        if (m != null && !m.muscles().isEmpty()) {
            String track = ctx.world().trackKey() == null ? null
                    : catalog.track(ctx.world().worldKey(), ctx.world().trackKey()).map(t -> t.name()).orElse(null);
            why.add(new Reason("💪", "Qué trabaja", "Trabaja " + join(labels(m.muscles())) + "."
                    + (track == null ? "" : " Encaja con tu ruta " + track + "."), "info"));
        }
        if (m != null) {
            Set<String> available = SafetyFilter.availableEquipment(ctx.user().profile());
            List<String> eq = m.equipment().stream().filter(e -> !e.equals("silla")).toList();
            if (eq.isEmpty()) {
                why.add(new Reason("🙌", "Sin equipo", "Solo necesitas tu cuerpo"
                        + (m.equipment().contains("silla") ? " y una silla firme" : "") + ": lo haces donde estés.",
                        "success"));
            } else if (SafetyFilter.hasEquipment(m, available)) {
                why.add(new Reason("🎒", "Con lo que tienes", "Usa " + join(labels(eq)) + ", que tienes disponible.",
                        "success"));
            }
            List<String> zones = h.injuredZones();
            if (!zones.isEmpty() && safety.cautions(m, ctx.user()).isEmpty()) {
                why.add(new Reason("🛡️", "Cuida tus zonas", "Elegido sin contraindicaciones para tu "
                        + join(zones.stream().map(z -> catalog.tagLabels().getOrDefault(z, z).toLowerCase(Locale.ROOT))
                                .toList()) + ".", "success"));
            }
            if (h.prudentMode() && !"alto".equals(m.impact())) {
                why.add(new Reason("🌱", "Prudente", "Es de impacto " + m.impact()
                        + ": encaja con el modo prudente mientras completas tu chequeo de salud.", "info"));
            }
            if (wm != null) {
                why.add(new Reason("📶", "A tu nivel", "Dificultad " + wm.level() + " de 5; tu nivel en este mundo es "
                        + ctx.world().level() + ".", "info"));
            }
        }
        try {
            for (String extra : registry.require(ctx.world().worldKey()).explainBlock(ctx, b)) {
                why.add(new Reason("✨", "Para ti", extra, "info"));
            }
        } catch (RuntimeException ignored) {
            // un mundo con una explicación rota no debe tumbar el detalle
        }

        // ── Dosis ──
        Double readiness = in.readiness() == null ? null : in.readiness().factor();
        List<String> dose = DoseText.explain(b.prescription(), readiness);

        // ── Ten en cuenta ──
        List<Reason> cons = new ArrayList<>();
        if (h.mustRefer()) {
            cons.add(new Reason("⛔", "Consulta primero", "Marcaste señales que un profesional debe revisar: "
                    + "mantén todo muy suave hasta tener su aval.", "danger"));
        }
        if (in.readiness() != null && in.readiness().factor() < 0.95) {
            cons.add(new Reason("🌙", "Hoy, más suave", in.readiness().label()
                    + (in.readiness().reasons().isEmpty() ? "" : " (" + String.join(", ", in.readiness().reasons()) + ")."),
                    "warn"));
        }
        if (m != null) {
            for (String tag : safety.cautions(m, ctx.user())) {
                cons.add(new Reason("⚠️", "Precaución: " + catalog.tagLabels().getOrDefault(tag, tag),
                        "Hazlo en un rango cómodo y sin dolor. Si molesta, cámbialo con 🔄 o pregúntale al asistente.",
                        "warn"));
            }
            if (!safety.allowed(m, ctx.user())) {
                cons.add(new Reason("🚫", "No recomendado para ti", "Según tu salud este ejercicio no te conviene: "
                        + "cámbialo por una alternativa.", "danger"));
            }
            if ("alto".equals(m.impact())) {
                cons.add(new Reason("🦘", "Impacto alto", "Aterriza suave, con rodillas flexionadas y alineadas con "
                        + "los pies. Si notas dolor en articulaciones, haz la versión sin salto.", "info"));
            }
            Set<String> available = SafetyFilter.availableEquipment(ctx.user().profile());
            List<String> missing = m.equipment().stream().filter(e -> !available.contains(e)).toList();
            if (!missing.isEmpty()) {
                cons.add(new Reason("🧰", "Te falta equipo", "Necesitas " + join(labels(missing))
                        + ". Si no lo tienes, cámbialo con 🔄 por una alternativa equivalente.", "warn"));
            }
        }
        if (h.prudentMode()) {
            cons.add(new Reason("🩺", "Modo prudente", "Intensidad moderada: deberías poder hablar mientras lo haces. "
                    + "Haz tu chequeo de salud (1 minuto) para desbloquear la intensidad alta.", "info"));
        } else if (!h.vigorousAllowed()) {
            cons.add(new Reason("🩺", "Intensidad moderada", h.moderateReason(), "warn"));
        }
        if (in.heartRateUnreliable()) {
            cons.add(new Reason("💓", "Guíate por el esfuerzo", "Con betabloqueadores el pulso no refleja el esfuerzo: "
                    + "usa la escala de esfuerzo (RPE) o la prueba del habla.", "info"));
        }
        if (in.hypoglycemiaRisk()) {
            cons.add(new Reason("🍌", "Azúcar estable", "Come algo antes, ten a mano un carbohidrato rápido y para si "
                    + "sientes temblor, sudor frío o mareo.", "warn"));
        }
        if (in.avoidContact() && "football".equals(ctx.world().worldKey())) {
            cons.add(new Reason("🩸", "Sin contacto", "Tomas anticoagulantes: evita choques, disputas y caídas.", "warn"));
        }
        if (in.noJumpingAtHome() && m != null && "alto".equals(m.impact())) {
            cons.add(new Reason("🏠", "En casa, sin saltos", "Nos dijiste que no puedes saltar en casa: haz la variante "
                    + "sin salto o hazlo fuera.", "info"));
        }
        cons.add(new Reason("👂", "Escucha a tu cuerpo", "El esfuerzo cansa; el dolor punzante o que aumenta no es "
                + "normal. Si aparece, detente y cambia el ejercicio.", "info"));

        return new Explanation(why, dose, cons, ps);
    }

    // ── Piezas ────────────────────────────────────────────────

    private Reason role(String blockType, MovementInfo m, String focus) {
        String type = blockType == null ? "" : blockType;
        return switch (type) {
            case "calentamiento" -> new Reason("🔥", "Prepara tu cuerpo", "Sube la temperatura y despierta las "
                    + "articulaciones que vas a usar: rindes más y reduces el riesgo de lesión.", "info");
            case "principal" -> new Reason("⭐", "El trabajo central de hoy", "Es uno de los ejercicios que más "
                    + "aportan a " + (focus == null || focus.isBlank() ? "esta sesión." : "«" + focus + "».")
                    + " Ponle tu mejor energía y técnica.", "info");
            case "accesorio" -> new Reason("🧩", "Complementa", "Refuerza músculos de apoyo para equilibrar el "
                    + "trabajo principal y prevenir lesiones.", "info");
            case "core" -> new Reason("🧱", "Tronco estable", "Un tronco fuerte transmite la fuerza entre piernas y "
                    + "brazos y protege tu espalda.", "info");
            case "tecnica" -> new Reason("🎯", "Técnica", "Repetir el gesto con calidad lo automatiza; la velocidad y "
                    + "la carga vienen después.", "info");
            case "movilidad" -> new Reason("🧘", "Movilidad", "Gana rango de movimiento y alivia la rigidez del día a "
                    + "día.", "info");
            case "vuelta_calma" -> new Reason("🌬️", "Vuelta a la calma", "Baja las pulsaciones y relaja la musculatura: "
                    + "te recuperas mejor para la próxima sesión.", "info");
            default -> new Reason("🏃", "Parte de tu sesión", m == null ? "Forma parte de tu plan de hoy."
                    : m.description() == null ? "Forma parte de tu plan de hoy." : firstSentence(m.description()),
                    "info");
        };
    }

    private ProgressionStatus progression(WorldContext ctx, String key, int stepIndex) {
        ProgressionInfo p = catalog.progression(ctx.world().worldKey(), key).orElse(null);
        if (p == null || p.steps().isEmpty()) return null;
        StepInfo current = p.steps().stream().filter(s -> s.index() == stepIndex).findFirst().orElse(null);
        StepInfo next = p.steps().stream().filter(s -> s.index() > stepIndex).findFirst().orElse(null);
        int position = (int) p.steps().stream().filter(s -> s.index() <= stepIndex).count();
        return new ProgressionStatus(p.key(), p.name(), Math.max(1, position), p.steps().size(),
                current == null ? null : current.name(), next == null ? null : next.name(),
                current == null ? null : unlockText(current.unlockCriteria()));
    }

    /** Criterio de desbloqueo en texto ({"type":"reps","sets":3,"reps":8} → "3 series de 8 repeticiones"). */
    static String unlockText(Map<String, Object> c) {
        if (c == null || c.isEmpty()) return null;
        Object type = c.get("type");
        Object sets = c.getOrDefault("sets", 1);
        if ("reps".equals(type)) return "Para avanzar: " + sets + " series de " + c.get("reps") + " repeticiones con buena técnica.";
        if ("hold".equals(type)) return "Para avanzar: " + sets + " series de " + c.get("seconds") + " s.";
        if ("pain".equals(type)) {
            return "Para avanzar: " + c.getOrDefault("sessions", 6) + " sesiones con dolor ≤ "
                    + c.getOrDefault("maxDuring", 3) + "/10 durante y al día siguiente.";
        }
        return null;
    }

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("tren_inferior", "tren inferior"), Map.entry("tren_superior", "tren superior"),
            Map.entry("cuadriceps", "cuádriceps"), Map.entry("gluteos", "glúteos"), Map.entry("triceps", "tríceps"),
            Map.entry("biceps", "bíceps"), Map.entry("isquiotibiales", "isquiotibiales"),
            Map.entry("cuerpo_completo", "cuerpo completo"), Map.entry("barra_dominadas", "barra de dominadas"),
            Map.entry("barra_olimpica", "barra olímpica"), Map.entry("balon", "balón"), Map.entry("maquina", "máquina"),
            Map.entry("foam_roller", "foam roller"), Map.entry("porteria", "portería"));

    private static List<String> labels(List<String> keys) {
        return keys.stream().map(k -> LABELS.getOrDefault(k, k.replace('_', ' '))).toList();
    }

    private static String join(List<String> items) {
        if (items.isEmpty()) return "";
        if (items.size() == 1) return items.get(0);
        return String.join(", ", items.subList(0, items.size() - 1)) + " y " + items.get(items.size() - 1);
    }

    static String firstSentence(String text) {
        String t = text.trim();
        int dot = t.indexOf(". ");
        return dot > 0 && dot < 160 ? t.substring(0, dot + 1) : t.length() > 160 ? t.substring(0, 157) + "…" : t;
    }
}
