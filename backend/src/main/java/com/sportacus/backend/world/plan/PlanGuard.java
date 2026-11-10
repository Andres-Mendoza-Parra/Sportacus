package com.sportacus.backend.world.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import com.sportacus.backend.world.core.GeneratedPlan;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedBlock;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedSession;
import com.sportacus.backend.world.core.Prescription;

/**
 * Reglas del núcleo que se aplican a TODO plan después de que lo genera el mundo (docs/22), en este orden:
 * <ol>
 * <li><b>Guardia clínica</b>: con señales que un profesional debe revisar ({@code mustRefer}) solo sesiones suaves;
 * sin intensidad vigorosa permitida, la intensidad se modera (sin "máxima", sin cargas pesadas, RIR ≥ 2).</li>
 * <li><b>Menores de 18</b>: sin cargas máximas (repeticiones bajas → 8–12 con RIR ≥ 2).</li>
 * <li><b>Días</b>: las sesiones propias por semana no superan los días del usuario (el partido y los entrenamientos
 * de equipo no cuentan: son compromisos externos).</li>
 * <li><b>Minutos</b>: ninguna sesión supera los minutos del usuario: se quitan bloques secundarios, luego series,
 * luego duración; los escalones de progresión conservan sus series del criterio.</li>
 * </ol>
 * Reglas puras (sin base de datos): probadas en {@code PlanGuardTest}.
 */
public final class PlanGuard {

    private PlanGuard() {
    }

    /** Sesiones que no son entrenamiento propio del usuario (no cuentan para días ni minutos). */
    static final Set<String> EXTERNAL_KINDS = Set.of("partido", "equipo");
    /** Mundos que ya resuelven por sí mismos la derivación con un plan suave. */
    static final Set<String> SELF_GUARDED_REFER = Set.of("base", "health");
    private static final int BLOCK_OVERHEAD_S = 30;

    /**
     * @param impactOf impacto de un movimiento ("bajo" | "medio" | "alto") o null si no existe
     */
    public record Inputs(String worldKey, int days, int minutes, boolean mustRefer, boolean vigorousAllowed,
            boolean minor, Function<String, String> impactOf) {
    }

    public static GeneratedPlan apply(GeneratedPlan plan, Inputs in) {
        List<GeneratedSession> sessions = new ArrayList<>();
        for (GeneratedSession s : plan.sessions()) {
            GeneratedSession g = s;
            if (in.mustRefer() && !SELF_GUARDED_REFER.contains(in.worldKey())) g = gentle(g, in);
            else if (!in.vigorousAllowed()) g = moderate(g, "Intensidad moderada: deberías poder hablar mientras entrenas.");
            if (in.minor()) g = youth(g);
            sessions.add(g);
        }
        sessions = capDays(sessions, in.days());
        List<GeneratedSession> out = new ArrayList<>();
        for (GeneratedSession s : sessions) out.add(capMinutes(s, in.minutes()));
        return new GeneratedPlan(plan.title(), plan.summary(), plan.weeks(), plan.score(), plan.rationale(), out);
    }

    // ── Guardia clínica ───────────────────────────────────────

    /** Con derivación pendiente: solo bloques de bajo impacto a intensidad suave; si no queda nada, sesión suave base. */
    static GeneratedSession gentle(GeneratedSession s, Inputs in) {
        if (EXTERNAL_KINDS.contains(s.kind())) {
            return withNotes(s, "Marcaste señales que un profesional debe revisar: consulta antes de jugar o entrenar con tu equipo.");
        }
        List<GeneratedBlock> kept = new ArrayList<>();
        for (GeneratedBlock b : s.blocks()) {
            String impact = b.movementKey() == null ? null : in.impactOf().apply(b.movementKey());
            if (!"bajo".equals(impact)) continue;
            if (b.prescription() != null && Set.of("interval", "distance").contains(b.prescription().type())) continue;
            kept.add(soften(b, true));
        }
        if (kept.stream().noneMatch(b -> !"calentamiento".equals(b.blockType()) && !"vuelta_calma".equals(b.blockType()))) {
            kept = gentleBase(in);
        }
        return new GeneratedSession(s.weekIndex(), s.dayIndex(), s.weekday(), "Sesión suave", s.focus(), "suave", null,
                join(s.notes(), "Marcaste señales que un profesional debe revisar: mientras tanto, solo movimiento "
                        + "suave. Si algo te duele o te falta el aire, detente."), kept);
    }

    private static List<GeneratedBlock> gentleBase(Inputs in) {
        List<GeneratedBlock> out = new ArrayList<>();
        addIf(out, in, "calentamiento", "hea_respiracion_diafragmatica", Prescription.time(120, "suave"));
        addIf(out, in, "principal", "hea_movilidad_articular_suave", Prescription.time(300, "suave"));
        addIf(out, in, "principal", "hea_caminata_suave", Prescription.time(480, "suave: puedes conversar"));
        addIf(out, in, "vuelta_calma", "hea_relajacion_guiada", Prescription.time(120, "suave"));
        return out;
    }

    private static void addIf(List<GeneratedBlock> out, Inputs in, String type, String key, Prescription p) {
        if (in.impactOf().apply(key) != null) out.add(GeneratedBlock.of(type, key, null, p));
    }

    /** Sin intensidad vigorosa: quita "máxima"/competición, cargas pesadas y el fallo. */
    static GeneratedSession moderate(GeneratedSession s, String note) {
        boolean changed = false;
        List<GeneratedBlock> blocks = new ArrayList<>();
        for (GeneratedBlock b : s.blocks()) {
            GeneratedBlock nb = soften(b, false);
            changed |= nb != b;
            blocks.add(nb);
        }
        String notes = EXTERNAL_KINDS.contains(s.kind()) ? join(s.notes(), "Juega a intensidad moderada hasta tener tu aval médico.")
                : changed ? join(s.notes(), note) : s.notes();
        return new GeneratedSession(s.weekIndex(), s.dayIndex(), s.weekday(), s.title(), s.focus(), s.kind(),
                changed ? null : s.estMinutes(), notes, blocks);
    }

    static boolean isVigorous(String intensity) {
        if (intensity == null) return false;
        String i = intensity.toLowerCase(Locale.ROOT);
        return i.contains("máxim") || i.contains("maxim") || i.contains("competici") || i.contains("vigoros")
                || i.contains("rpe 8") || i.contains("rpe 9") || i.contains("rpe 10") || i.contains("esprint")
                || i.contains("sprint") || i.contains("fuerte") || i.contains("90 %");
    }

    /** Suaviza una prescripción; devuelve el mismo bloque si no hacía falta. */
    static GeneratedBlock soften(GeneratedBlock b, boolean gentle) {
        Prescription p = b.prescription();
        if (p == null) return b;
        Prescription np = p;
        if (isVigorous(p.intensity()) || (gentle && p.intensity() != null)) {
            np = np.withIntensity(gentle ? "suave" : "moderada: deberías poder hablar");
        }
        if ("reps".equals(p.type())) {
            boolean heavy = (p.repsMin() != null && p.repsMin() < 6) || (p.reps() != null && p.reps() < 6 && p.loadKg() != null);
            if (heavy) np = rangeReps(np, 8, 12);
            int minRir = gentle ? 3 : 2;
            if (np.rir() != null && np.rir() < minRir) np = np.withRir(minRir);
            if (np.loadKg() != null && heavy) np = np.withLoad(null);
            if (gentle && np.sets() != null && np.sets() > 2) np = np.withSets(2);
        }
        if ("interval".equals(p.type()) && gentle) np = np.withIntensity("suave");
        return np == p ? b : new GeneratedBlock(b.blockType(), b.movementKey(), b.title(), np, b.progressionKey(),
                b.stepIndex(), b.notes());
    }

    private static Prescription rangeReps(Prescription p, int min, int max) {
        return new Prescription(p.type(), p.sets(), null, min, max, p.loadKg(), p.rir(), p.holdS(), p.durationS(),
                p.distanceM(), p.repeats(), p.workS(), p.recoverS(), p.restS(), p.paceSecPerKm(), p.intensity(),
                p.tempo(), p.painMax(), p.side(), p.cue());
    }

    /** Menores de 18: fuerza con cargas moderadas (8–12, RIR ≥ 2); la técnica antes que la carga. */
    static GeneratedSession youth(GeneratedSession s) {
        boolean changed = false;
        List<GeneratedBlock> blocks = new ArrayList<>();
        for (GeneratedBlock b : s.blocks()) {
            Prescription p = b.prescription();
            if (p != null && "reps".equals(p.type()) && p.repsMin() != null && p.repsMin() < 6) {
                Prescription np = rangeReps(p, 8, 12).withLoad(null);
                if (np.rir() == null || np.rir() < 2) np = np.withRir(2);
                blocks.add(new GeneratedBlock(b.blockType(), b.movementKey(), b.title(), np, b.progressionKey(),
                        b.stepIndex(), b.notes()));
                changed = true;
            } else {
                blocks.add(b);
            }
        }
        if (!changed) return s;
        return new GeneratedSession(s.weekIndex(), s.dayIndex(), s.weekday(), s.title(), s.focus(), s.kind(), null,
                join(s.notes(), "Como tienes menos de 18 años, priorizamos técnica y cargas moderadas (8–12 repeticiones)."),
                blocks);
    }

    // ── Días ──────────────────────────────────────────────────

    /** Por semana, deja como máximo {@code days} sesiones propias (conserva las primeras del orden del mundo). */
    static List<GeneratedSession> capDays(List<GeneratedSession> sessions, int days) {
        int max = Math.max(1, Math.min(7, days));
        List<GeneratedSession> out = new ArrayList<>();
        java.util.Map<Integer, Integer> perWeek = new java.util.HashMap<>();
        for (GeneratedSession s : sessions) {
            if (EXTERNAL_KINDS.contains(s.kind())) {
                out.add(s);
                continue;
            }
            int n = perWeek.merge(s.weekIndex(), 1, Integer::sum);
            if (n <= max) out.add(s);
        }
        return out;
    }

    // ── Minutos ───────────────────────────────────────────────

    static int seconds(GeneratedBlock b) {
        Prescription p = b.prescription();
        if (p == null) return 60 + BLOCK_OVERHEAD_S;
        int s = p.estimatedSeconds();
        if ("cada_lado".equals(p.side()) && Set.of("reps", "hold").contains(p.type())) s *= 2;
        return s + BLOCK_OVERHEAD_S;
    }

    static int minutesOf(List<GeneratedBlock> blocks) {
        return (int) Math.ceil(blocks.stream().mapToInt(PlanGuard::seconds).sum() / 60.0);
    }

    /** Orden en que se quitan bloques cuando no caben (primero los más prescindibles). */
    private static int dropPriority(GeneratedBlock b) {
        if (b.progressionKey() != null) return 99;
        return switch (b.blockType() == null ? "" : b.blockType()) {
            case "accesorio" -> 1;
            case "core" -> 2;
            case "movilidad" -> 3;
            case "tecnica" -> 4;
            case "intervalo" -> 5;
            case "principal" -> 6;
            default -> 90; // calentamiento y vuelta a la calma se acortan, no se quitan
        };
    }

    static GeneratedSession capMinutes(GeneratedSession s, int minutes) {
        if (EXTERNAL_KINDS.contains(s.kind())) return s;
        int limit = Math.max(10, minutes);
        List<GeneratedBlock> blocks = new ArrayList<>(s.blocks());
        int original = Math.max(minutesOf(blocks), s.estMinutes() == null ? 0 : s.estMinutes());
        if (original <= limit) {
            return new GeneratedSession(s.weekIndex(), s.dayIndex(), s.weekday(), s.title(), s.focus(), s.kind(),
                    Math.min(limit, Math.max(minutesOf(blocks), s.estMinutes() == null ? 0 : s.estMinutes())), s.notes(),
                    blocks);
        }
        // 1) Quitar bloques prescindibles (dejando al menos un bloque principal o de progresión).
        List<GeneratedBlock> byDrop = new ArrayList<>(blocks);
        byDrop.sort(Comparator.comparingInt(PlanGuard::dropPriority).thenComparing(b -> -blocks.indexOf(b)));
        for (GeneratedBlock candidate : byDrop) {
            if (minutesOf(blocks) <= limit || dropPriority(candidate) >= 90) break;
            if (dropPriority(candidate) == 6 && mainCount(blocks) <= 1) continue;
            blocks.remove(candidate);
        }
        // 2) Bajar series (las de progresión no bajan de 3 para poder desbloquear).
        for (int round = 0; round < 4 && minutesOf(blocks) > limit; round++) {
            for (int i = blocks.size() - 1; i >= 0 && minutesOf(blocks) > limit; i--) {
                GeneratedBlock b = blocks.get(i);
                Prescription p = b.prescription();
                if (p == null || p.sets() == null) continue;
                int floor = b.progressionKey() != null ? 3 : 1;
                if (p.sets() > floor) blocks.set(i, with(b, p.withSets(p.sets() - 1)));
            }
        }
        // 3) Acortar duraciones (tiempo, intervalos, distancia) en proporción.
        if (minutesOf(blocks) > limit) {
            double factor = limit * 60.0 / blocks.stream().mapToInt(PlanGuard::seconds).sum();
            for (int i = 0; i < blocks.size(); i++) blocks.set(i, scale(blocks.get(i), factor));
        }
        int est = Math.min(limit, minutesOf(blocks));
        return new GeneratedSession(s.weekIndex(), s.dayIndex(), s.weekday(), s.title(), s.focus(), s.kind(), est,
                join(s.notes(), "Ajustada a tus " + limit + " minutos disponibles."), blocks);
    }

    private static long mainCount(List<GeneratedBlock> blocks) {
        return blocks.stream().filter(b -> "principal".equals(b.blockType()) || b.progressionKey() != null).count();
    }

    private static GeneratedBlock scale(GeneratedBlock b, double factor) {
        Prescription p = b.prescription();
        if (p == null || factor >= 1) return b;
        Prescription np = switch (p.type()) {
            case "time" -> new Prescription(p.type(), p.sets(), p.reps(), p.repsMin(), p.repsMax(), p.loadKg(), p.rir(),
                    p.holdS(), roundTo(p.durationS() == null ? 300 : p.durationS(), factor, 30), p.distanceM(),
                    p.repeats(), p.workS(), p.recoverS(), p.restS(), p.paceSecPerKm(), p.intensity(), p.tempo(),
                    p.painMax(), p.side(), p.cue());
            case "interval" -> new Prescription(p.type(), p.sets(), p.reps(), p.repsMin(), p.repsMax(), p.loadKg(),
                    p.rir(), p.holdS(), p.durationS(), p.distanceM(),
                    Math.max(1, (int) Math.floor((p.repeats() == null ? 1 : p.repeats()) * factor)), p.workS(),
                    p.recoverS(), p.restS(), p.paceSecPerKm(), p.intensity(), p.tempo(), p.painMax(), p.side(), p.cue());
            case "distance" -> new Prescription(p.type(), p.sets(), p.reps(), p.repsMin(), p.repsMax(), p.loadKg(),
                    p.rir(), p.holdS(), p.durationS(), roundTo(p.distanceM() == null ? 1000 : p.distanceM(), factor, 100),
                    p.repeats(), p.workS(), p.recoverS(), p.restS(), p.paceSecPerKm(), p.intensity(), p.tempo(),
                    p.painMax(), p.side(), p.cue());
            default -> p;
        };
        return np == p ? b : with(b, np);
    }

    private static int roundTo(int value, double factor, int step) {
        return Math.max(step, (int) (Math.floor(value * factor / step) * step));
    }

    private static GeneratedBlock with(GeneratedBlock b, Prescription p) {
        return new GeneratedBlock(b.blockType(), b.movementKey(), b.title(), p, b.progressionKey(), b.stepIndex(),
                b.notes());
    }

    private static GeneratedSession withNotes(GeneratedSession s, String note) {
        return new GeneratedSession(s.weekIndex(), s.dayIndex(), s.weekday(), s.title(), s.focus(), s.kind(),
                s.estMinutes(), join(s.notes(), note), s.blocks());
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (a.contains(b)) return a;
        return a.trim() + " " + b;
    }
}
