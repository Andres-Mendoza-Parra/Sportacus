package com.sportacus.backend.worlds.gym;

import static com.sportacus.backend.world.form.Forms.multi;
import static com.sportacus.backend.world.form.Forms.number;
import static com.sportacus.backend.world.form.Forms.opt;
import static com.sportacus.backend.world.form.Forms.optional;
import static com.sportacus.backend.world.form.Forms.showIf;
import static com.sportacus.backend.world.form.Forms.single;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.catalog.CatalogModels.TrackInfo;
import com.sportacus.backend.world.catalog.CatalogModels.WorldMovement;
import com.sportacus.backend.world.catalog.SafetyFilter;
import com.sportacus.backend.world.core.DiscoveryInput;
import com.sportacus.backend.world.core.EnrollmentResult;
import com.sportacus.backend.world.core.EnrollmentResult.GoalSpec;
import com.sportacus.backend.world.core.Factor;
import com.sportacus.backend.world.core.GeneratedPlan;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedBlock;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedSession;
import com.sportacus.backend.world.core.LoggedSession;
import com.sportacus.backend.world.core.LoggedSession.LoggedBlock;
import com.sportacus.backend.world.core.PlanView;
import com.sportacus.backend.world.core.Prescription;
import com.sportacus.backend.world.core.SessionOutcome;
import com.sportacus.backend.world.core.SessionUi;
import com.sportacus.backend.world.core.UserContext;
import com.sportacus.backend.world.core.Widget;
import com.sportacus.backend.world.core.Widget.Axis;
import com.sportacus.backend.world.core.Widget.Insight;
import com.sportacus.backend.world.core.Widget.Stat;
import com.sportacus.backend.world.core.WorldContext;
import com.sportacus.backend.world.core.WorldModule;
import com.sportacus.backend.world.core.WorldToolkit;
import com.sportacus.backend.world.form.FormSpec;
import com.sportacus.backend.world.form.Forms;
import com.sportacus.backend.world.metrics.MetricRepository;
import com.sportacus.backend.world.plan.PlanRepository;
import com.sportacus.backend.world.session.SessionLogRepository;

/**
 * Mundo Gimnasio: fuerza e hipertrofia con cargas externas.
 * <ul>
 * <li>Split según días disponibles (full body, torso/pierna, empuje/jale/pierna).</li>
 * <li>Ranuras por patrón de movimiento; prescripción por ruta (rango de repeticiones, RIR, descanso).</li>
 * <li>Doble progresión: al completar todas las series en el tope del rango con RIR ≥ 1, sube la carga.</li>
 * <li>Métricas: 1RM estimado (Epley), series semanales por músculo (Schoenfeld 2017: 10–20), tonelaje.</li>
 * </ul>
 */
@Component
public class GymWorld implements WorldModule {

    public static final String KEY = "gym";
    static final double WEEKLY_SETS_TARGET = 10;
    static final List<String> RADAR_MUSCLES = List.of("pecho", "espalda", "hombros", "brazos", "piernas", "gluteos");

    private final WorldToolkit kit;
    private final MetricRepository metrics;
    private final SessionLogRepository sessionLogs;
    private final PlanRepository plans;

    public GymWorld(WorldToolkit kit, MetricRepository metrics, SessionLogRepository sessionLogs, PlanRepository plans) {
        this.kit = kit;
        this.metrics = metrics;
        this.sessionLogs = sessionLogs;
        this.plans = plans;
    }

    @Override
    public String key() {
        return KEY;
    }

    // ───────────────────────── Onboarding ─────────────────────────

    @Override
    public FormSpec onboardingForm(UserContext user) {
        return Forms.form("gym", "Tu entrenamiento en el gimnasio", "Así ajustamos el split, las series y las cargas.",
                single("g_goal", "¿Cuál es tu objetivo principal?", null,
                        opt("hipertrofia", "💪", "Ganar músculo"),
                        opt("fuerza", "🏆", "Ser más fuerte en los básicos"),
                        opt("recomposicion", "🔥", "Perder grasa sin perder músculo"),
                        opt("base", "🧱", "Aprender la técnica y crear el hábito")),
                single("g_experience", "¿Cuánto tiempo llevas entrenando con pesas?", null,
                        opt("0", "🌱", "Nunca o casi nunca"), opt("1", "🌿", "Menos de 6 meses"),
                        opt("2", "🌳", "Entre 6 meses y 2 años"), opt("3", "🏔️", "Más de 2 años")),
                single("g_place", "¿Dónde vas a entrenar?", null,
                        opt("gimnasio", "🏋️", "Gimnasio completo"),
                        opt("casa_mancuernas", "🏠", "En casa con mancuernas/banco"),
                        opt("casa_bandas", "🎗️", "En casa con bandas o poco equipo")),
                optional(multi("g_focus", "¿Quieres priorizar alguna zona?", "Le daremos una serie extra.", 2,
                        opt("pecho", "🫁", "Pecho"), opt("espalda", "🔙", "Espalda"), opt("hombros", "🤷", "Hombros"),
                        opt("brazos", "💪", "Brazos"), opt("piernas", "🦵", "Piernas"), opt("gluteos", "🍑", "Glúteos"))),
                optional(showIf(number("g_squat_5rm", "Si lo sabes: ¿con cuántos kg haces 5 sentadillas con barra?",
                        "Déjalo vacío si no lo sabes; lo calcularemos con tus primeras sesiones.", 10, 400, "kg"),
                        "g_experience", "2", "3")),
                optional(showIf(number("g_bench_5rm", "¿Y 5 repeticiones de press de banca?", null, 10, 300, "kg"),
                        "g_experience", "2", "3")),
                optional(showIf(number("g_deadlift_5rm", "¿Y 5 repeticiones de peso muerto?", null, 10, 450, "kg"),
                        "g_experience", "2", "3")));
    }

    @Override
    public List<Factor> discoveryFactors(DiscoveryInput in) {
        List<Factor> f = new ArrayList<>();
        Answers a = in.answers();
        UserContext u = in.user();
        if (a.contains("d_motivation", "fuerza_fisico")) f.add(Factor.of("motivacion", 25, "Quieres verte y sentirte más fuerte: es el terreno de las cargas"));
        if (a.contains("d_enjoy", "pesas")) f.add(Factor.of("gusto", 15, "Te atraen las pesas y las máquinas"));
        if ("numeros".equals(a.string("d_motivator"))) f.add(Factor.of("motivador", 10, "Te motivan los números: aquí cada kilo cuenta"));
        if (a.contains("d_experience", "gimnasio")) f.add(Factor.of("experiencia", 8, "Ya conoces el gimnasio"));
        boolean gym = u.profile().hasEnvironment("gimnasio");
        boolean weights = u.profile().hasEquipment("mancuernas") || u.profile().hasEquipment("kettlebell");
        if (gym) f.add(Factor.of("entorno", 8, "Tienes acceso a un gimnasio"));
        else if (!weights) f.add(Factor.of("entorno", -12, "Sin gimnasio ni pesas en casa el progreso con cargas es limitado"));
        if ("intensa".equals(a.string("d_intensity"))) f.add(Factor.of("intensidad", 4, "Buscas exigencia"));
        if ("suave".equals(a.string("d_intensity"))) f.add(Factor.of("intensidad", -6, "Prefieres algo suave"));
        if ("equipo".equals(a.string("d_social"))) f.add(Factor.of("social", -4, "Prefieres entrenar en equipo"));
        if (a.contains("d_motivation", "salud")) f.add(Factor.of("salud", -6, "Tu prioridad es recuperarte o cuidar tu salud"));
        if (u.health().mustRefer()) f.add(Factor.of("seguridad", -20, "Antes de entrenar con cargas conviene la valoración de un profesional"));
        if (u.profile().ageOr(30) >= 65) f.add(Factor.of("edad", -4, "Con la edad, empieza con cargas muy controladas"));
        return f;
    }

    @Override
    public EnrollmentResult enroll(UserContext user, Answers a) {
        String track = a.string("g_goal", "base");
        int exp = a.integer("g_experience", 0);
        int level = Math.min(5, Math.max(1, exp + 1));
        if ("0".equals(a.string("g_experience")) && !"base".equals(track)) level = 1;
        List<Factor> why = new ArrayList<>();
        why.add(Factor.of("ruta", 0, "Ruta " + track + " según tu objetivo"));
        why.add(Factor.of("nivel", 0, "Nivel " + level + " de 5 según tu experiencia"));

        Map<String, Object> loads = new LinkedHashMap<>();
        // 5RM ≈ 87 % del 1RM; empezamos al ~80 % del 5RM declarado para afinar la técnica la primera semana.
        putLoad(loads, "sentadilla_barra", a.number("g_squat_5rm"));
        putLoad(loads, "press_banca", a.number("g_bench_5rm"));
        putLoad(loads, "peso_muerto", a.number("g_deadlift_5rm"));
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("loads", loads);
        state.put("place", a.string("g_place", "gimnasio"));
        state.put("focus", a.list("g_focus"));

        List<GoalSpec> goals = new ArrayList<>();
        switch (track) {
            case "fuerza" -> goals.add(new GoalSpec("gym_fuerza", "Subir un 10 % tu 1RM estimado de sentadilla",
                    Map.of("metric", "gym_e1rm", "movement", "sentadilla_barra", "improvePct", 10),
                    LocalDate.now().plusWeeks(12)));
            case "hipertrofia" -> goals.add(new GoalSpec("gym_volumen", "Sostener 10+ series semanales por músculo durante 8 semanas",
                    Map.of("weeklySets", WEEKLY_SETS_TARGET, "weeks", 8), LocalDate.now().plusWeeks(8)));
            case "recomposicion" -> goals.add(new GoalSpec("gym_constancia", "Entrenar 3 o más días por semana durante 8 semanas",
                    Map.of("sessionsPerWeek", 3, "weeks", 8), LocalDate.now().plusWeeks(8)));
            default -> goals.add(new GoalSpec("gym_tecnica", "Completar 12 sesiones dominando los patrones básicos",
                    Map.of("sessions", 12), LocalDate.now().plusWeeks(6)));
        }
        List<String> notices = new ArrayList<>();
        if (!user.health().vigorousAllowed()) {
            notices.add("Mantendremos RIR 3 (lejos del fallo) hasta que confirmes el aval de un profesional.");
        }
        if (!user.health().injuredZones().isEmpty()) {
            notices.add("Excluimos los ejercicios que cargan tus zonas con molestias: " + String.join(", ", user.health().injuredZones()) + ".");
        }
        return new EnrollmentResult(track, level, a.asMap(), state, goals, Map.of(), why, notices);
    }

    private static void putLoad(Map<String, Object> loads, String movement, Double fiveRm) {
        if (fiveRm != null && fiveRm > 0) loads.put(movement, roundTo(fiveRm * 0.8, 2.5));
    }

    // ───────────────────────── Plan ─────────────────────────

    /** Ranuras por tipo de sesión: patrón de movimiento que debe llenar cada ejercicio. */
    private static final Map<String, List<String>> TEMPLATES = Map.of(
            "full_a", List.of("sentadilla", "empuje_horizontal", "jale_vertical", "bisagra", "core"),
            "full_b", List.of("bisagra", "empuje_vertical", "jale_horizontal", "zancada", "core"),
            "full_c", List.of("sentadilla", "empuje_horizontal", "jale_horizontal", "gluteo", "biceps", "triceps"),
            "torso", List.of("empuje_horizontal", "jale_horizontal", "empuje_vertical", "jale_vertical", "biceps", "triceps"),
            "pierna", List.of("sentadilla", "bisagra", "zancada", "aislamiento_pierna", "gemelo", "core"),
            "empuje", List.of("empuje_horizontal", "empuje_vertical", "empuje_horizontal", "hombro_lateral", "triceps"),
            "jale", List.of("jale_vertical", "jale_horizontal", "jale_horizontal", "biceps", "core"));

    private static final Map<String, String> TEMPLATE_TITLES = Map.of("full_a", "Cuerpo completo A",
            "full_b", "Cuerpo completo B", "full_c", "Cuerpo completo C", "torso", "Torso", "pierna", "Pierna",
            "empuje", "Empuje", "jale", "Jale");

    private static final Set<String> ACCESSORY_PATTERNS = Set.of("biceps", "triceps", "gemelo", "hombro_lateral",
            "aislamiento_pierna", "core", "gluteo");

    static List<String> split(int days) {
        return switch (Math.max(2, Math.min(6, days))) {
            case 2 -> List.of("full_a", "full_b");
            case 3 -> List.of("full_a", "full_b", "full_c");
            case 4 -> List.of("torso", "pierna", "torso", "pierna");
            case 5 -> List.of("empuje", "jale", "pierna", "torso", "pierna");
            default -> List.of("empuje", "jale", "pierna", "empuje", "jale", "pierna");
        };
    }

    static String splitName(int days) {
        return switch (Math.max(2, Math.min(6, days))) {
            case 2, 3 -> "cuerpo completo";
            case 4 -> "torso/pierna";
            case 5 -> "empuje/jale/pierna + torso/pierna";
            default -> "empuje/jale/pierna ×2";
        };
    }

    @Override
    public GeneratedPlan generatePlan(WorldContext ctx) {
        UserContext user = ctx.user();
        TrackInfo track = kit.catalog().track(KEY, ctx.world().trackKey() == null ? "base" : ctx.world().trackKey())
                .orElseThrow();
        int days = Math.max(2, Math.min(6, user.profile().daysOr(3)));
        int minutes = user.profile().minutesOr(60);
        Set<String> equipment = equipmentFor(ctx);
        List<WorldMovement> usable = kit.usableMovements(KEY, user, equipment);
        Set<String> liked = kit.liked(user.userId(), KEY);
        Set<String> disliked = kit.disliked(user.userId(), KEY);
        Map<String, Object> loads = ctx.world().stateMap("loads");
        List<String> focus = ctx.world().profile().list("g_focus");
        int level = ctx.world().level();
        Random rnd = WorldToolkit.stableRandom(user.userId(), KEY + ctx.world().trackKey());

        int sets = track.configInt("sets", 3);
        int repsMin = track.configInt("repsMin", 8);
        int repsMax = track.configInt("repsMax", 12);
        int rir = user.health().vigorousAllowed() ? track.configInt("rir", 2) : 3;
        int rest = track.configInt("restS", 90);
        // Cada ejercicio ≈ series × (40 s de trabajo + descanso); 8 min de calentamiento.
        int perExerciseS = sets * (40 + rest);
        int maxExercises = Math.max(3, Math.min(7, (minutes - 8) * 60 / perExerciseS));

        List<String> templates = split(days);
        List<Integer> weekdays = kit.weekdays(user.profile(), days);
        WorldMovement warmup = usable.stream().filter(m -> "calentamiento".equals(m.role())).findFirst().orElse(null);
        Map<String, Integer> usesPerMovement = new LinkedHashMap<>();
        List<GeneratedSession> sessions = new ArrayList<>();
        int excluded = kit.catalog().movements(KEY).size() - usable.size();

        for (int d = 0; d < templates.size(); d++) {
            String template = templates.get(d);
            List<String> slots = TEMPLATES.get(template).subList(0, Math.min(maxExercises, TEMPLATES.get(template).size()));
            List<GeneratedBlock> blocks = new ArrayList<>();
            if (warmup != null) {
                blocks.add(GeneratedBlock.of("calentamiento", warmup.key(), warmup.name(),
                        Prescription.time(300, "suave").withCue("Después, 2 series de aproximación ligeras en el primer ejercicio")));
            }
            Set<String> usedToday = new HashSet<>();
            for (String pattern : slots) {
                List<WorldMovement> candidates = usable.stream()
                        .filter(m -> pattern.equals(m.pattern()) && !"calentamiento".equals(m.role()))
                        .filter(m -> m.level() <= level + 1).toList();
                if (candidates.isEmpty()) continue;
                WorldMovement pick = WorldToolkit.pick(candidates, usedToday, m -> {
                    double s = 10 - Math.abs(m.level() - level) * 2;
                    if (m.attrTrue("compound") && !ACCESSORY_PATTERNS.contains(pattern)) s += 3;
                    if (liked.contains(m.key())) s += 4;
                    if (disliked.contains(m.key())) s -= 8;
                    s -= usesPerMovement.getOrDefault(m.key(), 0) * 2.5; // variedad entre días
                    return s;
                }, rnd);
                if (pick == null) continue;
                usedToday.add(pick.key());
                usesPerMovement.merge(pick.key(), 1, Integer::sum);
                boolean accessory = ACCESSORY_PATTERNS.contains(pattern) || !pick.attrTrue("compound");
                int blockSets = accessory ? Math.max(2, sets - ("fuerza".equals(track.key()) ? 1 : 0)) : sets;
                if (focus.stream().anyMatch(z -> pick.movement().muscles().stream().anyMatch(mu -> zoneOf(mu).equals(z)))) {
                    blockSets++;
                }
                Prescription p = "core".equals(pattern) && pick.attrTrue("hold")
                        ? Prescription.hold(blockSets, 30 + level * 5, 45)
                        : accessory
                                ? Prescription.reps(blockSets, Math.max(8, repsMin + 2), Math.max(12, repsMax + 2), Math.min(rest, 75))
                                : Prescription.reps(blockSets, repsMin, repsMax, rest);
                p = p.withRir(rir);
                Object load = loads.get(pick.key());
                if (load instanceof Number n && "reps".equals(p.type())) p = p.withLoad(n.doubleValue());
                blocks.add(GeneratedBlock.of(accessory ? "accesorio" : "principal", pick.key(), pick.name(), p));
            }
            Integer weekday = d < weekdays.size() ? weekdays.get(d) : null;
            sessions.add(new GeneratedSession(0, d, weekday, TEMPLATE_TITLES.get(template),
                    focusOf(blocks), "fuerza", null, null, blocks));
        }

        List<Factor> why = new ArrayList<>();
        why.add(Factor.of("split", 0, "Split " + splitName(days) + " para " + days + " días por semana"));
        why.add(Factor.of("rango", 0, repsMin + "–" + repsMax + " repeticiones con RIR " + rir + " (ruta " + track.name() + ")"));
        why.add(Factor.of("volumen", 0, "Objetivo de ~10 series semanales por músculo (Schoenfeld et al., 2017)"));
        if (excluded > 0) why.add(Factor.of("seguridad", 0, excluded + " ejercicios descartados por tu equipamiento o tu salud"));
        String title = "Plan de " + track.name().toLowerCase() + " · " + splitName(days);
        return new GeneratedPlan(title, "Semana tipo que se repite; la carga sube sola cuando dominas el rango.", 1,
                null, why, sessions);
    }

    private Set<String> equipmentFor(WorldContext ctx) {
        String place = ctx.world().stateString("place");
        // Si entrena en casa, solo cuenta su propio equipo (aunque tenga un gimnasio en sus entornos).
        Set<String> eq = "gimnasio".equals(place) || place == null
                ? SafetyFilter.availableEquipment(ctx.user().profile())
                : new HashSet<>(ctx.user().profile().equipment());
        if ("gimnasio".equals(place)) {
            eq.addAll(List.of("barra_olimpica", "rack", "banco", "mancuernas", "polea", "maquina", "barra_dominadas",
                    "paralelas", "kettlebell", "bandas"));
        } else if ("casa_mancuernas".equals(place)) {
            eq.addAll(List.of("mancuernas", "banco"));
        } else if ("casa_bandas".equals(place)) {
            eq.add("bandas");
        }
        return eq;
    }

    private static String focusOf(List<GeneratedBlock> blocks) {
        return String.join(" · ", blocks.stream().filter(b -> !"calentamiento".equals(b.blockType()))
                .map(GeneratedBlock::title).limit(3).toList());
    }

    // ───────────────────────── Evaluación ─────────────────────────

    @Override
    public SessionOutcome evaluateSession(WorldContext ctx, LoggedSession session) {
        SessionOutcome.Builder out = SessionOutcome.builder();
        Map<String, List<LoggedBlock>> byMovement = new LinkedHashMap<>();
        for (LoggedBlock b : session.blocks()) {
            if (b.movementKey() != null && b.completed()) byMovement.computeIfAbsent(b.movementKey(), k -> new ArrayList<>()).add(b);
        }
        Map<String, Object> loads = new LinkedHashMap<>(ctx.world().stateMap("loads"));
        boolean loadsChanged = false;
        double tonnage = 0;
        Map<String, Integer> setsPerZone = new LinkedHashMap<>();
        Double bodyweight = ctx.user().profile().weightKg();

        for (var entry : byMovement.entrySet()) {
            WorldMovement wm = kit.catalog().worldMovement(KEY, entry.getKey()).orElse(null);
            if (wm == null) continue;
            List<LoggedBlock> sets = entry.getValue();
            // Series efectivas por zona muscular (músculo principal).
            String zone = zoneOf(wm.movement().firstMuscle() == null ? "" : wm.movement().firstMuscle());
            if (!zone.isEmpty()) setsPerZone.merge(zone, sets.size(), Integer::sum);

            double bestE1rm = 0;
            for (LoggedBlock s : sets) {
                if (s.loadKg() != null && s.reps() != null && s.loadKg() > 0 && s.reps() > 0) {
                    tonnage += s.loadKg() * s.reps();
                    if (s.reps() <= 12) bestE1rm = Math.max(bestE1rm, epley(s.loadKg(), s.reps()));
                }
            }
            if (bestE1rm > 0 && wm.attrTrue("loadable")) {
                double rounded = Math.round(bestE1rm * 10) / 10.0;
                double previous = metrics.seriesWhere(ctx.world().id(), "gym_e1rm", "movement", wm.key(), 50).stream()
                        .mapToDouble(Widget.Point::value).max().orElse(0);
                out.metric("gym_e1rm", rounded, Map.of("movement", wm.key(), "name", wm.name()));
                if (previous > 0 && rounded > previous) {
                    out.highlight("¡Récord! 1RM estimado de " + wm.name() + ": " + fmt(rounded) + " kg");
                    out.achievement("gym_primer_record", "Primer récord personal", "🥇");
                }
                if (bodyweight != null && "sentadilla_barra".equals(wm.key()) && rounded >= bodyweight) {
                    out.achievement("gym_sentadilla_peso_corporal", "Sentadilla con tu peso corporal", "🦵");
                }
                if (bodyweight != null && "press_banca".equals(wm.key()) && rounded >= bodyweight) {
                    out.achievement("gym_banca_peso_corporal", "Press de banca con tu peso corporal", "🏋️");
                }
            }

            // Doble progresión sobre lo planificado.
            Prescription planned = sets.get(0).planned();
            Double lastLoad = sets.stream().map(LoggedBlock::loadKg).filter(x -> x != null && x > 0)
                    .reduce((x, y) -> y).orElse(null);
            if (planned != null && "reps".equals(planned.type()) && lastLoad != null && wm.attrTrue("loadable")) {
                int top = planned.repsMax() != null ? planned.repsMax() : planned.reps() != null ? planned.reps() : 10;
                int bottom = planned.repsMin() != null ? planned.repsMin() : top;
                int plannedSets = planned.sets() == null ? sets.size() : planned.sets();
                boolean allTop = sets.size() >= plannedSets && sets.stream()
                        .allMatch(s -> s.reps() != null && s.reps() >= top && (s.rir() == null || s.rir() >= 1));
                double avgReps = sets.stream().filter(s -> s.reps() != null).mapToInt(LoggedBlock::reps).average().orElse(top);
                double next = lastLoad;
                if (allTop) {
                    next = lastLoad + wm.attrNumber("increment", 2.5);
                    out.highlight("Sube a " + fmt(next) + " kg en " + wm.name() + " la próxima vez");
                } else if (avgReps < bottom - 2) {
                    next = roundTo(lastLoad * 0.95, 2.5);
                    out.highlight("Baja a " + fmt(next) + " kg en " + wm.name() + " para volver a tu rango");
                }
                if (!Double.valueOf(next).equals(asDouble(loads.get(wm.key())))) {
                    loads.put(wm.key(), next);
                    loadsChanged = true;
                    applyLoadToPlan(ctx, wm.key(), next);
                }
            }
        }
        setsPerZone.forEach((zone, n) -> out.metric("gym_series_musculo", n, Map.of("muscle", zone)));
        if (tonnage > 0) out.metric("gym_tonelaje", Math.round(tonnage));
        if (loadsChanged) out.state("loads", loads);
        int total = sessionLogs.countWorld(ctx.world().id());
        if (total >= 12 && ctx.world().level() < 3) {
            out.level(ctx.world().level() + 1);
            out.highlight("Subes a nivel " + (ctx.world().level() + 1) + ": desbloqueamos variantes más exigentes");
            out.regeneratePlan();
        }
        return out.build();
    }

    private void applyLoadToPlan(WorldContext ctx, String movementKey, double kg) {
        for (var block : plans.activeBlocksOf(ctx.world().id(), movementKey)) {
            plans.updatePrescription(block.id(), block.prescription().withLoad(kg));
        }
    }

    static double epley(double load, int reps) {
        return reps == 1 ? load : load * (1 + reps / 30.0);
    }

    static double roundTo(double value, double step) {
        return Math.round(value / step) * step;
    }

    private static Double asDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** Músculo del catálogo → zona del radar. */
    static String zoneOf(String muscle) {
        return switch (muscle) {
            case "pecho" -> "pecho";
            case "espalda", "dorsales", "trapecio", "lumbares" -> "espalda";
            case "hombros", "deltoides" -> "hombros";
            case "biceps", "triceps", "antebrazos" -> "brazos";
            case "cuadriceps", "isquiotibiales", "gemelos", "aductores" -> "piernas";
            case "gluteos" -> "gluteos";
            default -> "";
        };
    }

    private static final Map<String, String> FIVE_RM_TESTS = Map.of("gym_5rm_sentadilla", "sentadilla_barra",
            "gym_5rm_banca", "press_banca", "gym_5rm_peso_muerto", "peso_muerto");

    @Override
    public com.sportacus.backend.world.core.AssessmentOutcome onAssessment(WorldContext ctx, String key, double value) {
        String movement = FIVE_RM_TESTS.get(key);
        if (movement == null || value <= 0) return com.sportacus.backend.world.core.AssessmentOutcome.none();
        double e1rm = Math.round(epley(value, 5) * 10) / 10.0;
        double working = roundTo(value * 0.8, 2.5);
        Map<String, Object> loads = new LinkedHashMap<>(ctx.world().stateMap("loads"));
        loads.put(movement, working);
        applyLoadToPlan(ctx, movement, working);
        String name = kit.catalog().movement(movement).map(m -> m.name()).orElse(movement);
        SessionOutcome effects = SessionOutcome.builder()
                .metric("gym_e1rm", e1rm, Map.of("movement", movement, "name", name))
                .state("loads", loads).build();
        return new com.sportacus.backend.world.core.AssessmentOutcome(null, List.of(
                "1RM estimado de " + name + ": " + fmt(e1rm) + " kg",
                "Carga de trabajo para tus series: " + fmt(working) + " kg"), effects);
    }

    // ───────────────────────── Dashboard ─────────────────────────

    @Override
    public List<Widget> dashboard(WorldContext ctx, PlanView plan) {
        List<Widget> w = new ArrayList<>();
        w.add(Widget.nextSession("Tu próxima sesión", plan.nextSession()));
        Map<String, Double> weekly = metrics.sumByContextSince(ctx.world().id(), "gym_series_musculo", "muscle", 7);
        double totalSets = weekly.values().stream().mapToDouble(Double::doubleValue).sum();
        double tonnage = metrics.sumLastDays(ctx.world().id(), "gym_tonelaje", 7);
        w.add(Widget.statRow("semana", "Esta semana", List.of(
                new Stat("Sesiones", plan.doneThisWeek() + "/" + plan.currentWeekSessions().size(), null, null,
                        plan.doneThisWeek() >= plan.currentWeekSessions().size() ? "good" : null),
                new Stat("Series", String.valueOf((int) totalSets), null, "efectivas", null),
                new Stat("Tonelaje", tonnage >= 1000 ? fmt(Math.round(tonnage / 100) / 10.0) : fmt(tonnage),
                        tonnage >= 1000 ? "t" : "kg", null, null))));
        w.add(Widget.radar("series_musculo", "Volumen por músculo", "Series de los últimos 7 días (objetivo: 10)",
                RADAR_MUSCLES.stream().map(m -> {
                    double sets = weekly.getOrDefault(m, 0.0);
                    return new Axis(label(m), Math.min(100, sets / WEEKLY_SETS_TARGET * 100), fmt(sets) + "/10 series");
                }).toList()));
        Map<String, Double> e1rm = metrics.latestByContext(ctx.world().id(), "gym_e1rm", "movement");
        String mainLift = List.of("sentadilla_barra", "press_banca", "peso_muerto").stream().filter(e1rm::containsKey)
                .findFirst().orElse(e1rm.keySet().stream().findFirst().orElse(null));
        if (mainLift != null) {
            String name = kit.catalog().movement(mainLift).map(m -> m.name()).orElse(mainLift);
            w.add(Widget.line("e1rm_principal", "Fuerza estimada · " + name, "1RM estimado (Epley)", "kg",
                    metrics.seriesWhere(ctx.world().id(), "gym_e1rm", "movement", mainLift, 20), "up", null));
        }
        w.add(Widget.insights("Para ti", insights(ctx, weekly, plan)));
        return w;
    }

    private List<Insight> insights(WorldContext ctx, Map<String, Double> weekly, PlanView plan) {
        List<Insight> out = new ArrayList<>();
        HealthSummary h = ctx.user().health();
        if (!h.injuredZones().isEmpty()) {
            out.add(Insight.of("🩹", "Cuidamos tus zonas sensibles",
                    "Tu plan evita cargar: " + String.join(", ", h.injuredZones()) + ". Si duele, para y cambia el ejercicio.", "info"));
        }
        if (!weekly.isEmpty()) {
            RADAR_MUSCLES.stream().filter(m -> weekly.getOrDefault(m, 0.0) < 4).findFirst().ifPresent(m -> out.add(
                    Insight.of("📉", label(m) + " va corto de volumen",
                            "Llevas " + fmt(weekly.getOrDefault(m, 0.0)) + " series esta semana; apunta a 10.", "warn")));
        }
        if (ctx.world().stateMap("loads").isEmpty()) {
            out.add(Insight.of("🎯", "Encuentra tus cargas",
                    "Elige un peso con el que te queden 2 repeticiones en reserva (RIR 2) y regístralo: desde ahí la app sube la carga por ti.", "info"));
        }
        if (plan.doneThisWeek() == 0) {
            out.add(new Insight("▶️", "Empieza la semana", "Tu primera sesión te espera.", "info", "Entrenar", "/session"));
        }
        if (out.isEmpty()) out.add(Insight.of("✅", "Todo en orden", "Sigue registrando tus series para afinar las cargas.", "good"));
        return out;
    }

    static String label(String zone) {
        return switch (zone) {
            case "pecho" -> "Pecho";
            case "espalda" -> "Espalda";
            case "hombros" -> "Hombros";
            case "brazos" -> "Brazos";
            case "piernas" -> "Piernas";
            case "gluteos" -> "Glúteos";
            default -> zone;
        };
    }

    @Override
    public List<Widget> progress(WorldContext ctx) {
        List<Widget> w = new ArrayList<>();
        Map<String, Double> e1rm = metrics.latestByContext(ctx.world().id(), "gym_e1rm", "movement");
        e1rm.keySet().stream().limit(4).forEach(mk -> {
            String name = kit.catalog().movement(mk).map(m -> m.name()).orElse(mk);
            w.add(Widget.line("e1rm_" + mk, name, "1RM estimado (Epley)", "kg",
                    metrics.seriesWhere(ctx.world().id(), "gym_e1rm", "movement", mk, 30), "up", null));
        });
        w.add(Widget.weekBars("tonelaje", "Tonelaje semanal", "kg movidos por semana", "kg",
                metrics.weekly(ctx.world().id(), "gym_tonelaje", 8, "sum"), null));
        w.add(Widget.weekBars("sesiones", "Sesiones por semana", null, "sesiones",
                metrics.weekly(ctx.world().id(), "minutos_sesion", 8, "count"), (double) ctx.user().profile().daysOr(3)));
        Map<String, Double> month = metrics.sumByContextSince(ctx.world().id(), "gym_series_musculo", "muscle", 28);
        w.add(Widget.bars("series_mes", "Series por músculo (4 semanas)", "Objetivo: 40 por músculo",
                RADAR_MUSCLES.stream().map(m -> new Widget.Bar(label(m),
                        Math.min(100, month.getOrDefault(m, 0.0) / 40 * 100), fmt(month.getOrDefault(m, 0.0)) + " series"))
                        .toList()));
        if (e1rm.isEmpty()) {
            w.add(Widget.notice("sin_datos", "info", "Aún no hay récords",
                    "Registra peso y repeticiones en tus series y aquí verás crecer tu fuerza."));
        }
        return w;
    }

    @Override
    public SessionUi sessionUi() {
        return new SessionUi("sets", List.of(), List.of("rpe"), List.of("reps", "load", "rir"), List.of(
                "RIR 2 = podrías hacer 2 repeticiones más con buena técnica.",
                "Controla la bajada (2–3 s) y sube con intención.",
                "Si una serie duele (no es esfuerzo, es dolor), detente y cambia el ejercicio."));
    }

    @Override
    public String assistantPersona(WorldContext ctx) {
        return """
                Eres un entrenador de fuerza e hipertrofia. Hablas de series, repeticiones, RIR (repeticiones en reserva),
                técnica de los patrones básicos, sobrecarga progresiva y volumen semanal por músculo (10–20 series).
                El usuario sigue la ruta "%s" en nivel %d de 5. Sugiere cargas solo en relación con lo que ya registró
                (p. ej. "sube 2,5 kg"), nunca cifras absolutas inventadas. Prioriza la técnica sobre la carga.
                """.formatted(ctx.world().trackKey(), ctx.world().level());
    }
}
