package com.sportacus.backend.worlds.calisthenics;

import static com.sportacus.backend.world.form.Forms.multi;
import static com.sportacus.backend.world.form.Forms.number;
import static com.sportacus.backend.world.form.Forms.opt;
import static com.sportacus.backend.world.form.Forms.optional;
import static com.sportacus.backend.world.form.Forms.single;
import static com.sportacus.backend.worlds.calisthenics.CalisthenicsRules.progressionKey;
import static com.sportacus.backend.worlds.calisthenics.CalisthenicsRules.treeOf;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.catalog.CatalogModels.ProgressionInfo;
import com.sportacus.backend.world.catalog.CatalogModels.StepInfo;
import com.sportacus.backend.world.catalog.CatalogModels.TrackInfo;
import com.sportacus.backend.world.catalog.CatalogModels.WorldMovement;
import com.sportacus.backend.world.core.AssessmentOutcome;
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
import com.sportacus.backend.world.core.Widget.Insight;
import com.sportacus.backend.world.core.Widget.Stat;
import com.sportacus.backend.world.core.Widget.Tree;
import com.sportacus.backend.world.core.Widget.TreeStep;
import com.sportacus.backend.world.core.WorldContext;
import com.sportacus.backend.world.core.WorldModule;
import com.sportacus.backend.world.core.WorldToolkit;
import com.sportacus.backend.world.enrollment.UserWorldRepository;
import com.sportacus.backend.world.form.FormSpec;
import com.sportacus.backend.world.form.Forms;
import com.sportacus.backend.world.metrics.MetricRepository;
import com.sportacus.backend.world.plan.PlanRepository;
import com.sportacus.backend.world.session.SessionLogRepository;
import com.sportacus.backend.worlds.calisthenics.CalisthenicsRules.Flags;
import com.sportacus.backend.worlds.calisthenics.CalisthenicsRules.Maxes;
import com.sportacus.backend.worlds.calisthenics.CalisthenicsRules.Placement;
import com.sportacus.backend.worlds.calisthenics.CalisthenicsRules.SetLog;

/**
 * Mundo Calistenia: dominar el peso corporal mediante árboles de habilidades.
 * <ul>
 * <li>Cada árbol (empuje, jale, piernas, core, fondos, parada de manos) es una progresión de escalones con un
 * criterio de desbloqueo (p. ej. 3×12 limpias o 3×30 s).</li>
 * <li>El onboarding ubica al usuario en cada árbol a partir de sus marcas máximas ({@link CalisthenicsRules}).</li>
 * <li>Plan: el escalón actual de cada árbol es el bloque principal; si sobra tiempo, volumen del escalón anterior.
 * Si el escalón exige equipo que no hay o no es seguro, se usa el escalón seguro más cercano.</li>
 * <li>Evaluación: desbloqueo al cumplir el criterio; sostenimientos que suben 5 s; retroceso tras 2 sesiones muy por
 * debajo; métricas de mejor serie, sostenimiento y escalones.</li>
 * </ul>
 */
@Component
public class CalisthenicsWorld implements WorldModule {

    public static final String KEY = "calisthenics";
    static final Set<String> PARK_EQUIPMENT = Set.of("barra_dominadas", "paralelas", "banco");
    static final int WARMUP_S = 360;
    static final int COOLDOWN_S = 300;

    /** Movimiento alcanzado → logro. */
    private static final Map<String, String[]> STEP_ACHIEVEMENTS = Map.of(
            "flexiones", new String[] { "cal_flexion_completa", "Flexión completa desbloqueada", "🫸" },
            "dominadas", new String[] { "cal_dominada_desbloqueada", "Desbloqueaste la dominada", "🏆" },
            "fondos_paralelas", new String[] { "cal_fondos_paralelas", "Fondos en paralelas", "💪" },
            "parada_manos_pared", new String[] { "cal_parada_pared", "Parada de manos en la pared", "🤸" },
            "parada_manos_libre", new String[] { "cal_parada_libre", "Parada de manos libre", "⭐" },
            "l_sit", new String[] { "cal_l_sit", "Llegaste al L-sit", "🎯" },
            "pistol", new String[] { "cal_pistol", "Pistol squat desbloqueada", "🦵" },
            "transicion_muscle_up", new String[] { "cal_muscle_up", "Camino al muscle-up", "🚀" });

    /** Tests → árbol que recolocan y movimiento base cuya métrica alimentan. */
    private static final Map<String, String[]> TESTS = Map.of(
            "cal_test_flexiones", new String[] { "empuje", "flexiones", "reps" },
            "cal_test_dominadas", new String[] { "jale", "dominadas", "reps" },
            "cal_test_fondos", new String[] { "fondos", "fondos_paralelas", "reps" },
            "cal_test_hollow", new String[] { "core", "hollow_hold", "hold" },
            "cal_test_colgado", new String[] { "jale", "colgado_barra", "hold" },
            "cal_test_sentadillas", new String[] { "piernas", "sentadilla_peso_corporal", "reps" });

    private static final Map<String, String> EQUIPMENT_LABELS = Map.of("barra_dominadas", "una barra de dominadas",
            "paralelas", "paralelas", "anillas", "anillas", "banco", "un banco", "bandas", "bandas elásticas");

    private final WorldToolkit kit;
    private final MetricRepository metrics;
    private final SessionLogRepository sessionLogs;
    private final PlanRepository plans;
    private final UserWorldRepository userWorlds;

    public CalisthenicsWorld(WorldToolkit kit, MetricRepository metrics, SessionLogRepository sessionLogs,
            PlanRepository plans, UserWorldRepository userWorlds) {
        this.kit = kit;
        this.metrics = metrics;
        this.sessionLogs = sessionLogs;
        this.plans = plans;
        this.userWorlds = userWorlds;
    }

    @Override
    public String key() {
        return KEY;
    }

    // ───────────────────────── Onboarding ─────────────────────────

    @Override
    public FormSpec onboardingForm(UserContext user) {
        return Forms.form("calisthenics", "Tu punto de partida en calistenia",
                "Con tus marcas te ubicamos en el escalón justo de cada árbol de habilidades.",
                single("c_goal", "¿Qué quieres lograr con tu peso corporal?", null,
                        opt("base", "🌱", "Construir una base sólida", "Flexiones, remo, sentadilla y plancha bien hechas"),
                        opt("fuerza", "💥", "Ser más fuerte", "Dominadas, fondos y pistol squat"),
                        opt("habilidades", "⭐", "Aprender habilidades", "Parada de manos, L-sit, muscle-up"),
                        opt("forma", "🏃", "Ponerme en forma sin gimnasio", "En casa o en el parque")),
                optional(multi("c_skills", "¿Persigues alguna habilidad en concreto?", "Le daremos prioridad en tu plan.", 3,
                        opt("primera_dominada", "🧗", "Mi primera dominada"), opt("parada_manos", "🤸", "Parada de manos"),
                        opt("l_sit", "🎯", "L-sit"), opt("muscle_up", "🚀", "Muscle-up"),
                        opt("pistol", "🦵", "Pistol squat"), opt("fondos", "💪", "Fondos en paralelas"))),
                number("c_pushups", "¿Cuántas flexiones completas haces seguidas?",
                        "Pecho cerca del suelo y cuerpo recto. Si ninguna, pon 0.", 0, 150, "reps"),
                number("c_pullups", "¿Cuántas dominadas estrictas haces seguidas?",
                        "Desde brazos estirados hasta pasar la barbilla. 0 es un punto de partida válido.", 0, 60, "reps"),
                number("c_dips", "¿Cuántos fondos en paralelas haces seguidos?", "Si nunca los has probado, pon 0.", 0, 80, "reps"),
                number("c_plank", "¿Cuántos segundos aguantas en plancha?", "Antebrazos en el suelo y cuerpo recto.", 0, 600, "s"),
                number("c_squats", "¿Cuántas sentadillas profundas haces seguidas?", null, 0, 300, "reps"),
                single("c_handstand", "¿Qué tan cerca estás de la parada de manos?", null,
                        opt("nunca", "🌱", "Nunca la he intentado"), opt("pica", "🔺", "Hago flexiones en pica"),
                        opt("pared", "🧱", "Aguanto en la pared"), opt("libre", "⭐", "Sostengo unos segundos sin pared")),
                single("c_place", "¿Dónde vas a entrenar?", null,
                        opt("casa", "🏠", "En casa"), opt("parque", "🌳", "En un parque con barras"),
                        opt("ambos", "🔁", "Casa y parque")),
                optional(multi("c_equipment", "¿Qué tienes a mano en casa?", "En el parque ya contamos barra, paralelas y banco.",
                        opt("barra_dominadas", "➖", "Barra de dominadas"), opt("paralelas", "🟰", "Paralelas o dip station"),
                        opt("anillas", "⭕", "Anillas"), opt("bandas", "🎗️", "Bandas elásticas"),
                        opt("ninguno", "🚫", "Nada de esto"))));
    }

    @Override
    public List<Factor> discoveryFactors(DiscoveryInput in) {
        List<Factor> f = new ArrayList<>();
        Answers a = in.answers();
        UserContext u = in.user();
        if (a.contains("d_motivation", "habilidades")) f.add(Factor.of("motivacion", 25, "Quieres aprender habilidades: aquí cada ejercicio es un escalón que desbloqueas"));
        if (a.contains("d_motivation", "fuerza_fisico")) f.add(Factor.of("fuerza", 8, "Ganarás fuerza real con tu propio peso"));
        if (a.contains("d_enjoy", "peso_corporal")) f.add(Factor.of("gusto", 15, "Te gusta entrenar con tu peso corporal"));
        if (a.contains("d_enjoy", "aire_libre")) f.add(Factor.of("aire_libre", 5, "Las barras del parque son tu gimnasio al aire libre"));
        if ("retos".equals(a.string("d_motivator"))) f.add(Factor.of("motivador", 12, "Te motivan los retos: desbloquear la siguiente habilidad es el reto perfecto"));
        if ("juego".equals(a.string("d_motivator"))) f.add(Factor.of("motivador", 4, "Los árboles de habilidades funcionan como un juego de niveles"));
        if (a.contains("d_experience", "calistenia")) f.add(Factor.of("experiencia", 8, "Ya tienes experiencia en calistenia"));
        boolean gym = u.profile().hasEnvironment("gimnasio");
        if (u.profile().hasEnvironment("parque")) f.add(Factor.of("entorno", 8, "Tienes un parque cerca: barras y paralelas gratis"));
        else if (u.profile().hasEnvironment("casa") && !gym) f.add(Factor.of("entorno", 6, "Sin gimnasio: tu cuerpo es todo el equipo que necesitas"));
        if (u.profile().hasEquipment("barra_dominadas")) f.add(Factor.of("equipo", 4, "Tienes barra de dominadas en casa"));
        if ("equipo".equals(a.string("d_social"))) f.add(Factor.of("social", -6, "Prefieres deportes de equipo; aquí se entrena sobre todo solo"));
        if ("suave".equals(a.string("d_intensity"))) f.add(Factor.of("intensidad", -3, "Prefieres algo suave (aunque hay escalones muy accesibles)"));
        Double bmi = u.profile().bmi();
        if (bmi != null && bmi >= 35) f.add(Factor.of("imc", -12, "Con tu peso corporal como carga, las articulaciones reciben mucho esfuerzo: mejor empezar con algo de menor impacto"));
        else if (bmi != null && bmi >= 30) f.add(Factor.of("imc", -4, "Empezarías con variantes inclinadas y asistidas"));
        if (u.health().hasAny("muneca", "hombro")) f.add(Factor.of("articulaciones", -6, "Muñecas y hombros sostienen casi todo en calistenia"));
        if (u.health().mustRefer()) f.add(Factor.of("seguridad", -20, "Antes de entrenar conviene la valoración de un profesional"));
        if (u.profile().ageOr(30) >= 65) f.add(Factor.of("edad", -6, "Con la edad conviene una progresión muy gradual"));
        return f;
    }

    @Override
    public EnrollmentResult enroll(UserContext user, Answers a) {
        HealthSummary h = user.health();
        Maxes maxes = new Maxes(a.integer("c_pushups", 0), a.integer("c_pullups", 0), a.integer("c_dips", 0),
                a.integer("c_plank", 0), a.integer("c_squats", 0), a.string("c_handstand", "nunca"));
        List<String> skills = a.listWithoutNone("c_skills");
        String goal = a.string("c_goal", "base");
        String track = switch (goal) {
            case "fuerza" -> "fuerza_relativa";
            case "habilidades" -> "habilidades";
            default -> "fundamentos";
        };
        List<Factor> why = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        // Las habilidades (invertidos, muscle-up) exigen una base mínima de empuje y jale.
        if ("habilidades".equals(track) && maxes.pushups() < 5 && maxes.pullups() == 0) {
            track = "fundamentos";
            why.add(Factor.of("ruta", 0, "Empiezas en Fundamentos: las habilidades piden al menos 5 flexiones o 1 dominada"));
            notices.add("Cuando llegues a la flexión completa podrás cambiar a la ruta Habilidades.");
        } else {
            why.add(Factor.of("ruta", 0, "Ruta " + trackName(track) + " según tu objetivo"));
        }

        final String chosenTrack = track;
        List<String> trees = treesFor(track, skills);
        Double bmi = user.profile().bmi();
        Flags flags = new Flags((bmi != null && bmi >= 30) || user.profile().ageOr(30) >= 60 || h.hasAny("muneca", "hombro"),
                h.hasLowerLimbIssue(), user.profile().ageOr(30) >= 65, skills.contains("l_sit"));
        Map<String, Integer> steps = new LinkedHashMap<>();
        Map<String, Integer> lastIndex = new LinkedHashMap<>();
        for (String tree : trees) {
            ProgressionInfo prog = progression(tree);
            if (prog == null) continue;
            Placement p = CalisthenicsRules.place(tree, maxes, flags);
            int step = Math.min(p.step(), prog.lastIndex());
            steps.put(prog.key(), step);
            lastIndex.put(prog.key(), prog.lastIndex());
            why.add(Factor.of("ubicacion_" + tree, 0, prog.name() + ": " + p.basis() + " → empiezas en «"
                    + prog.step(step).name() + "» (escalón " + (step + 1) + " de " + prog.steps().size() + ")"));
        }
        int level = CalisthenicsRules.level(steps, lastIndex);
        why.add(Factor.of("nivel", 0, "Nivel " + level + " de 5: el progreso medio de tus árboles"));
        if (trees.size() > trackTrees(chosenTrack).size()) {
            why.add(Factor.of("habilidades", 0, "Sumamos árboles por tus habilidades objetivo: "
                    + trees.stream().filter(t -> !trackTrees(chosenTrack).contains(t)).map(this::treeName).collect(Collectors.joining(", "))));
        }

        String place = a.string("c_place", "casa");
        List<String> homeEquipment = a.listWithoutNone("c_equipment");
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("trees", trees);
        state.put("skills", skills);
        state.put("place", place);
        state.put("equipment", homeEquipment);
        state.put("maxes", maxesMap(maxes));

        if (trees.contains("jale") && "casa".equals(place) && !homeEquipment.contains("barra_dominadas")
                && !user.profile().hasEquipment("barra_dominadas")) {
            notices.add("Sin barra de dominadas el árbol de jale avanza poco: una barra de puerta o ir al parque lo desbloquea. Mientras tanto usamos alternativas sin barra.");
        }
        if (trees.contains("fondos") && "casa".equals(place) && !homeEquipment.contains("paralelas")) {
            notices.add("En casa los fondos se quedan en la silla; con paralelas (o en el parque) avanzas por todo el árbol.");
        }
        if (!h.vigorousAllowed()) {
            notices.add("Hasta que confirmes el aval de un profesional dejaremos 3 repeticiones en reserva y nada de ir al fallo.");
        }
        if (!h.injuredZones().isEmpty()) {
            notices.add("Cuidamos tus zonas con molestias (" + String.join(", ", h.injuredZones())
                    + "): si un escalón las carga, entrenas el escalón seguro más cercano.");
        }
        if (bmi != null && bmi >= 30) {
            notices.add("Empezamos con variantes inclinadas y asistidas para cuidar muñecas, hombros y rodillas.");
        }
        return new EnrollmentResult(track, level, a.asMap(), state, goals(track, skills, steps, maxes), steps, why, notices);
    }

    private List<GoalSpec> goals(String track, List<String> skills, Map<String, Integer> steps, Maxes maxes) {
        List<GoalSpec> goals = new ArrayList<>();
        LocalDate now = LocalDate.now();
        Set<String> wanted = new LinkedHashSet<>(skills);
        if (maxes.pullups() == 0 && steps.containsKey("cal_jale")) wanted.add("primera_dominada");
        for (String s : wanted) {
            if (goals.size() >= 2) break;
            switch (s) {
                case "primera_dominada" -> {
                    if (maxes.pullups() == 0) goals.add(new GoalSpec("cal_primera_dominada", "Desbloquear tu primera dominada",
                            Map.of("progression", "cal_jale", "step", stepOf("jale", "dominadas", 4)), now.plusWeeks(12)));
                }
                case "l_sit" -> goals.add(new GoalSpec("cal_l_sit_30", "Sostener 30 s de L-sit",
                        Map.of("metric", "cal_sostenimiento", "movement", "l_sit", "seconds", 30), now.plusWeeks(16)));
                case "parada_manos" -> goals.add(new GoalSpec("cal_parada_pared", "Sostener 45 s de parada de manos en la pared",
                        Map.of("progression", "cal_parada_manos", "step", stepOf("parada_manos", "parada_manos_pared", 3) + 1),
                        now.plusWeeks(12)));
                case "muscle_up" -> goals.add(new GoalSpec("cal_muscle_up", "Llegar a la transición de muscle-up",
                        Map.of("progression", "cal_jale", "step", stepOf("jale", "transicion_muscle_up", 8)), now.plusWeeks(24)));
                case "pistol" -> goals.add(new GoalSpec("cal_pistol", "Desbloquear la pistol squat",
                        Map.of("progression", "cal_piernas", "step", stepOf("piernas", "pistol", 6)), now.plusWeeks(16)));
                case "fondos" -> goals.add(new GoalSpec("cal_fondos_10", "Hacer 10 fondos seguidos en paralelas",
                        Map.of("metric", "cal_max_reps", "movement", "fondos_paralelas", "reps", 10), now.plusWeeks(12)));
                default -> {
                }
            }
        }
        int gain = "fundamentos".equals(track) ? 4 : 3;
        goals.add(new GoalSpec("cal_escalones", "Desbloquear " + gain + " escalones nuevos en 8 semanas",
                Map.of("metric", "cal_escalones", "gain", gain), now.plusWeeks(8)));
        return goals;
    }

    /** Árboles de la ruta + los de las habilidades objetivo que no estén ya. */
    static List<String> treesFor(String track, List<String> skills) {
        List<String> trees = new ArrayList<>(trackTrees(track));
        for (String s : skills) {
            String t = CalisthenicsRules.SKILL_TREE.get(s);
            if (t != null && !trees.contains(t) && trees.size() < 6) trees.add(t);
        }
        return trees;
    }

    static List<String> trackTrees(String track) {
        return switch (track) {
            case "fuerza_relativa" -> List.of("empuje", "jale", "fondos", "piernas", "core");
            case "habilidades" -> List.of("parada_manos", "jale", "fondos", "core");
            default -> List.of("empuje", "jale", "piernas", "core");
        };
    }

    private static Map<String, Object> maxesMap(Maxes m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pushups", m.pushups());
        out.put("pullups", m.pullups());
        out.put("dips", m.dips());
        out.put("plankS", m.plankS());
        out.put("squats", m.squats());
        out.put("handstand", m.handstand());
        return out;
    }

    private static Maxes maxesOf(Map<String, Object> m) {
        return new Maxes(CalisthenicsRules.num(m.get("pushups"), 0), CalisthenicsRules.num(m.get("pullups"), 0),
                CalisthenicsRules.num(m.get("dips"), 0), CalisthenicsRules.num(m.get("plankS"), 0),
                CalisthenicsRules.num(m.get("squats"), 0), m.get("handstand") == null ? "nunca" : String.valueOf(m.get("handstand")));
    }

    // ───────────────────────── Plan ─────────────────────────

    /** Bloque elegido para un árbol: el escalón actual o el sustituto seguro más cercano. */
    record TreeChoice(String tree, ProgressionInfo prog, int currentStep, StepInfo step, WorldMovement movement,
            boolean onStep, String note) {
    }

    @Override
    public GeneratedPlan generatePlan(WorldContext ctx) {
        UserContext user = ctx.user();
        String trackKey = ctx.world().trackKey() == null ? "fundamentos" : ctx.world().trackKey();
        TrackInfo track = kit.catalog().track(KEY, trackKey).orElseThrow();
        List<String> trees = treesOf(ctx, track);
        Map<String, Integer> steps = userWorlds.progressionSteps(ctx.world().id());
        Set<String> equipment = equipmentFor(ctx);
        Map<String, WorldMovement> usable = new LinkedHashMap<>();
        kit.usableMovements(KEY, user, equipment).forEach(m -> usable.put(m.key(), m));
        Set<String> safe = kit.safety().filter(kit.catalog().movements(KEY), user).stream().map(WorldMovement::key)
                .collect(Collectors.toSet());
        Map<String, Object> holds = ctx.world().stateMap("holds");
        boolean gentle = !user.health().vigorousAllowed();
        int days = Math.max(2, Math.min(5, user.profile().daysOr(3)));
        int minutes = Math.max(20, user.profile().minutesOr(45));
        Set<String> focusTrees = ctx.world().profile().listWithoutNone("c_skills").stream()
                .map(CalisthenicsRules.SKILL_TREE::get).filter(Objects::nonNull).collect(Collectors.toSet());

        List<Factor> why = new ArrayList<>();
        Map<String, TreeChoice> choices = new LinkedHashMap<>();
        for (String tree : trees) {
            ProgressionInfo prog = progression(tree);
            if (prog == null) continue;
            int current = Math.min(steps.getOrDefault(prog.key(), 0), prog.lastIndex());
            TreeChoice c = choose(tree, prog, current, usable, safe, equipment);
            if (c == null) {
                why.add(Factor.of("sin_" + tree, 0, prog.name() + ": hoy no hay una variante segura con tu equipo; lo retomaremos cuando cambie"));
                continue;
            }
            choices.put(tree, c);
            if (!c.onStep()) why.add(Factor.of("sustituto_" + tree, 0, c.note()));
        }
        List<String> active = new ArrayList<>(choices.keySet());
        if (active.isEmpty()) {
            return new GeneratedPlan("Calistenia · sin ejercicios disponibles", "Revisa tu equipo o tu chequeo de salud.", 1, null, why, List.of());
        }

        // ¿Cuántos árboles caben por sesión? Primero con 3 series; si no alcanza para 2 estímulos semanales, con 2.
        int budget = (minutes - 11) * 60;
        int setsCap = 3;
        int perSession = Math.max(1, budget / averageBlockSeconds(choices.values(), holds, setsCap));
        int needed = CalisthenicsRules.neededPerSession(active.size(), days);
        if (perSession < needed && perSession < active.size()) {
            setsCap = 2;
            perSession = Math.max(1, budget / averageBlockSeconds(choices.values(), holds, setsCap));
            why.add(Factor.of("tiempo", 0, "Con " + minutes + " min por sesión usamos 2 series por escalón para entrenar más árboles"));
        }
        List<List<String>> schedule = CalisthenicsRules.schedule(active, days, perSession);
        List<Integer> weekdays = kit.weekdays(user.profile(), days);
        WorldMovement warmup = usable.get("calentamiento_calistenia");
        WorldMovement inversionWarmup = usable.get("preparacion_invertidos");
        WorldMovement cooldown = usable.get("vuelta_calma_calistenia");
        Map<String, Integer> frequency = new LinkedHashMap<>();
        boolean anyVolume = false;
        List<GeneratedSession> sessions = new ArrayList<>();

        for (int d = 0; d < schedule.size(); d++) {
            List<String> dayTrees = schedule.get(d);
            List<GeneratedBlock> blocks = new ArrayList<>();
            int used = 0;
            WorldMovement wu = dayTrees.contains("parada_manos") && inversionWarmup != null ? inversionWarmup : warmup;
            if (wu != null) {
                blocks.add(GeneratedBlock.of("calentamiento", wu.key(), wu.name(), Prescription.time(WARMUP_S, "suave")
                        .withCue("Muñecas y hombros listos antes de cargar")));
            }
            for (String tree : dayTrees) {
                TreeChoice c = choices.get(tree);
                Prescription p = mainPrescription(c, holds, setsCap, gentle);
                GeneratedBlock b = GeneratedBlock.of("principal", c.movement().key(), c.onStep() ? c.step().name() : c.movement().name(), p);
                if (c.onStep()) {
                    b = b.withProgression(c.prog().key(), c.currentStep())
                            .withNotes(c.prog().name() + " · escalón " + (c.currentStep() + 1) + " de " + c.prog().steps().size());
                } else {
                    b = b.withNotes(c.note());
                }
                blocks.add(b);
                used += blockSeconds(p);
                frequency.merge(tree, 1, Integer::sum);
            }
            // Volumen extra del escalón anterior si sobra tiempo (primero los árboles de las habilidades objetivo).
            List<String> volumeOrder = new ArrayList<>(dayTrees);
            volumeOrder.sort((x, y) -> Boolean.compare(focusTrees.contains(y), focusTrees.contains(x)));
            int extras = 0;
            for (String tree : volumeOrder) {
                if (extras >= dayTrees.size()) break;
                TreeChoice c = choices.get(tree);
                if (!c.onStep() || c.currentStep() == 0) continue;
                StepInfo prev = c.prog().step(c.currentStep() - 1);
                WorldMovement pm = usable.get(prev.movementKey());
                if (pm == null || prev.target() == null) continue;
                Prescription vp = prev.target().withSets(2);
                if (gentle && "reps".equals(vp.type())) vp = vp.withRir(3);
                if (used + blockSeconds(vp) > budget) continue;
                blocks.add(GeneratedBlock.of("accesorio", pm.key(), "Volumen · " + prev.name(), vp
                        .withCue("Series cómodas del escalón anterior: técnica perfecta y recorrido completo"))
                        .withNotes("Volumen fácil para consolidar " + c.prog().name().toLowerCase()));
                used += blockSeconds(vp);
                extras++;
                anyVolume = true;
            }
            if (cooldown != null) {
                blocks.add(GeneratedBlock.of("vuelta_calma", cooldown.key(), cooldown.name(), Prescription.time(COOLDOWN_S, "suave")));
            }
            int est = Math.round((used + (wu != null ? WARMUP_S : 0) + (cooldown != null ? COOLDOWN_S : 0)) / 60f);
            Integer weekday = d < weekdays.size() ? weekdays.get(d) : null;
            String title = dayTrees.stream().map(this::treeName).collect(Collectors.joining(" + "));
            String focus = dayTrees.stream().map(t -> choices.get(t)).map(c -> c.onStep() ? c.step().name() : c.movement().name())
                    .collect(Collectors.joining(" · "));
            sessions.add(new GeneratedSession(0, d, weekday, title, focus, "calistenia", Math.max(10, est),
                    "Al cumplir el criterio de un escalón se desbloquea el siguiente.", blocks));
        }

        int minFreq = frequency.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        int maxFreq = frequency.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        why.add(0, Factor.of("reparto", 0, days + " sesiones por semana: cada árbol se entrena "
                + (minFreq == maxFreq ? minFreq : minFreq + "–" + maxFreq) + " veces"
                + (minFreq < 2 ? " (con más minutos por sesión llegarías a 2 en todos)" : "")));
        why.add(1, Factor.of("escalones", 0, "El bloque principal de cada árbol es tu escalón actual; se desbloquea el siguiente al cumplir su criterio (p. ej. 3 × 12 limpias)"));
        if (anyVolume) why.add(Factor.of("volumen", 0, "Con el tiempo que sobra sumas series del escalón anterior: volumen fácil que consolida la técnica"));
        if (gentle) why.add(Factor.of("seguridad", 0, "Sin aval médico confirmado: 3 repeticiones en reserva y sin ir al fallo"));
        String title = "Calistenia · " + track.name().toLowerCase() + " · " + active.size() + " árboles";
        return new GeneratedPlan(title, "Semana tipo que se repite; cada escalón que desbloqueas actualiza tu plan.", 1,
                null, why, sessions);
    }

    /**
     * Escalón actual si es utilizable; si no, el escalón utilizable más cercano por debajo; si tampoco, una
     * alternativa del mismo patrón marcada como sustituta en el contenido.
     */
    private TreeChoice choose(String tree, ProgressionInfo prog, int current, Map<String, WorldMovement> usable,
            Set<String> safe, Set<String> equipment) {
        StepInfo step = prog.step(current);
        WorldMovement m = usable.get(step.movementKey());
        if (m != null) return new TreeChoice(tree, prog, current, step, m, true, null);
        String reason = reasonFor(step.movementKey(), safe, equipment);
        for (int j = current - 1; j >= 0; j--) {
            StepInfo lower = prog.step(j);
            WorldMovement lm = usable.get(lower.movementKey());
            if (lm != null) {
                return new TreeChoice(tree, prog, current, lower, lm, false, prog.name() + ": «" + step.name() + "» " + reason
                        + "; mientras tanto trabajas «" + lower.name() + "»");
            }
        }
        WorldMovement alt = usable.values().stream()
                .filter(x -> tree.equals(x.pattern()) && x.attrTrue("fallback")).findFirst().orElse(null);
        if (alt == null) return null;
        StepInfo pseudo = new StepInfo(current, alt.name(), alt.key(), Prescription.reps(3, 8, 12, 60), Map.of());
        return new TreeChoice(tree, prog, current, pseudo, alt, false, prog.name() + ": «" + step.name() + "» " + reason
                + "; mientras tanto trabajas «" + alt.name() + "»");
    }

    private String reasonFor(String movementKey, Set<String> safe, Set<String> equipment) {
        if (!safe.contains(movementKey)) return "no es seguro con tus molestias actuales";
        List<String> missing = kit.catalog().movement(movementKey).map(mi -> mi.equipment().stream()
                .filter(e -> !equipment.contains(e)).map(e -> EQUIPMENT_LABELS.getOrDefault(e, e)).toList()).orElse(List.of());
        return missing.isEmpty() ? "no está disponible" : "necesita " + String.join(" y ", missing);
    }

    private Prescription mainPrescription(TreeChoice c, Map<String, Object> holds, int setsCap, boolean gentle) {
        Prescription p = c.step().target() == null ? Prescription.reps(3, 8, 12, 60) : c.step().target();
        if (c.onStep() && "hold".equals(p.type())) {
            Integer custom = holdOverride(holds, c.prog().key(), c.currentStep());
            if (custom != null) p = withHold(p, custom);
        }
        if (p.sets() != null) {
            // Los intentos cortos de equilibrio (5 series) conservan 2 series más que el resto.
            int max = "hold".equals(p.type()) && p.sets() > 3 ? setsCap + 2 : setsCap;
            if (p.sets() > max) p = p.withSets(max);
        }
        if ("reps".equals(p.type())) p = p.withRir(gentle ? 3 : 1);
        if (c.onStep() && c.currentStep() < c.prog().lastIndex()) {
            StepInfo next = c.prog().step(c.currentStep() + 1);
            p = p.withCue("Desbloqueas «" + next.name() + "» con " + criteriaText(c.step().unlockCriteria())
                    + (gentle ? " (sin llegar al fallo)" : ""));
        } else if (c.onStep()) {
            p = p.withCue("Último escalón del árbol: " + criteriaText(c.step().unlockCriteria()) + " y lo dominas");
        } else {
            p = p.withCue("Sustituto mientras no puedas hacer tu escalón");
        }
        return p;
    }

    static Integer holdOverride(Map<String, Object> holds, String progKey, int step) {
        if (holds.get(progKey) instanceof Map<?, ?> h && h.get("step") instanceof Number s && s.intValue() == step
                && h.get("s") instanceof Number sec) {
            return sec.intValue();
        }
        return null;
    }

    static Prescription withHold(Prescription p, int seconds) {
        return new Prescription(p.type(), p.sets(), p.reps(), p.repsMin(), p.repsMax(), p.loadKg(), p.rir(), seconds,
                p.durationS(), p.distanceM(), p.repeats(), p.workS(), p.recoverS(), p.restS(), p.paceSecPerKm(),
                p.intensity(), p.tempo(), p.painMax(), p.side(), p.cue());
    }

    /** Duración de un bloque: trabajo (3 s por repetición, el doble si es por lado) + descansos + transición. */
    static int blockSeconds(Prescription p) {
        int base = p.estimatedSeconds();
        if ("cada_lado".equals(p.side())) {
            int s = p.sets() == null ? 1 : p.sets();
            base += base - Math.max(0, s - 1) * (p.restS() == null ? 0 : p.restS());
        }
        return base + 45;
    }

    private int averageBlockSeconds(java.util.Collection<TreeChoice> choices, Map<String, Object> holds, int setsCap) {
        return Math.max(60, (int) choices.stream().mapToInt(c -> blockSeconds(mainPrescription(c, holds, setsCap, false)))
                .average().orElse(300));
    }

    static String criteriaText(Map<String, Object> crit) {
        if (crit == null || crit.isEmpty()) return "series limpias";
        int sets = CalisthenicsRules.criteriaSets(crit);
        int v = CalisthenicsRules.criteriaValue(crit);
        return CalisthenicsRules.isHold(crit) ? sets + " × " + v + " s" : sets + " × " + v + " limpias";
    }

    private List<String> treesOf(WorldContext ctx, TrackInfo track) {
        Object stored = ctx.world().stateValue("trees");
        if (stored instanceof List<?> l && !l.isEmpty()) return l.stream().map(String::valueOf).toList();
        if (track != null && track.config("trees") instanceof List<?> l) return l.stream().map(String::valueOf).toList();
        return trackTrees(ctx.world().trackKey() == null ? "fundamentos" : ctx.world().trackKey());
    }

    private List<String> treesOf(WorldContext ctx) {
        return treesOf(ctx, kit.catalog().track(KEY, ctx.world().trackKey() == null ? "fundamentos" : ctx.world().trackKey())
                .orElse(null));
    }

    /** Equipo para calistenia: lo de casa + lo del parque si entrena allí; siempre una silla. */
    private Set<String> equipmentFor(WorldContext ctx) {
        Set<String> eq = new HashSet<>(ctx.user().profile().equipment() == null ? List.of() : ctx.user().profile().equipment());
        Object stored = ctx.world().stateValue("equipment");
        if (stored instanceof List<?> l) l.forEach(x -> eq.add(String.valueOf(x)));
        String place = ctx.world().stateString("place");
        if (place == null || "parque".equals(place) || "ambos".equals(place)) eq.addAll(PARK_EQUIPMENT);
        eq.add("silla");
        return eq;
    }

    // ───────────────────────── Evaluación ─────────────────────────

    @Override
    public SessionOutcome evaluateSession(WorldContext ctx, LoggedSession session) {
        SessionOutcome.Builder out = SessionOutcome.builder();
        UUID uw = ctx.world().id();
        Map<String, Integer> steps = new LinkedHashMap<>(userWorlds.progressionSteps(uw));
        Map<String, List<LoggedBlock>> byMovement = new LinkedHashMap<>();
        Map<String, List<LoggedBlock>> byProgression = new LinkedHashMap<>();
        for (LoggedBlock b : session.blocks()) {
            if (!b.completed() || b.movementKey() == null) continue;
            if ("calentamiento".equals(b.blockType()) || "vuelta_calma".equals(b.blockType())) continue;
            byMovement.computeIfAbsent(b.movementKey(), k -> new ArrayList<>()).add(b);
            if (b.progressionKey() != null && b.stepIndex() != null) {
                byProgression.computeIfAbsent(b.progressionKey(), k -> new ArrayList<>()).add(b);
            }
        }

        // Métricas: mejor serie y mejor sostenimiento por movimiento, con récords personales. Los bloques de
        // volumen (accesorio) son series cómodas a propósito: no cuentan como marca.
        boolean declaredNoPullups = CalisthenicsRules.num(ctx.world().stateMap("maxes").get("pullups"), 0) == 0;
        for (var e : byMovement.entrySet()) {
            if (e.getValue().stream().allMatch(b -> "accesorio".equals(b.blockType()))) continue;
            String mk = e.getKey();
            String name = kit.catalog().movement(mk).map(m -> m.name()).orElse(mk);
            int bestReps = e.getValue().stream().map(LoggedBlock::reps).filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0);
            int bestHold = e.getValue().stream().map(LoggedBlock::holdS).filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0);
            if (bestReps > 0) {
                double prev = best(uw, "cal_max_reps", mk);
                out.metric("cal_max_reps", bestReps, Map.of("movement", mk, "name", name));
                if (prev > 0 && bestReps > prev) out.highlight("¡Récord en " + name + ": " + bestReps + " repeticiones en una serie!");
            }
            if (bestHold > 0) {
                double prev = best(uw, "cal_sostenimiento", mk);
                out.metric("cal_sostenimiento", bestHold, Map.of("movement", mk, "name", name));
                if (prev > 0 && bestHold > prev) out.highlight("¡Récord de sostenimiento en " + name + ": " + bestHold + " s!");
            }
            if ("dominadas".equals(mk) && bestReps >= 1 && declaredNoPullups) out.achievement("cal_primera_dominada", "Tu primera dominada", "🧗");
        }

        Map<String, Object> holds = new LinkedHashMap<>(ctx.world().stateMap("holds"));
        Map<String, Object> struggle = new LinkedHashMap<>(ctx.world().stateMap("struggle"));
        List<String> mastered = new ArrayList<>(stringList(ctx.world().stateValue("mastered")));
        boolean regress = false;

        for (var e : byProgression.entrySet()) {
            String pk = e.getKey();
            ProgressionInfo prog = kit.catalog().progression(KEY, pk).orElse(null);
            if (prog == null) continue;
            int current = Math.min(steps.getOrDefault(pk, 0), prog.lastIndex());
            List<LoggedBlock> sets = e.getValue();
            if (sets.get(0).stepIndex() != current) continue; // plan desactualizado: no evaluamos otro escalón
            StepInfo step = prog.step(current);
            Map<String, Object> crit = step.unlockCriteria();
            List<SetLog> logs = sets.stream().map(s -> new SetLog(s.reps(), s.holdS())).toList();
            boolean hold = CalisthenicsRules.isHold(crit);

            if (CalisthenicsRules.criteriaMet(crit, logs)) {
                struggle.remove(pk);
                holds.remove(pk);
                if (current < prog.lastIndex()) {
                    StepInfo next = prog.step(current + 1);
                    out.step(pk, current + 1);
                    steps.put(pk, current + 1);
                    out.highlight("¡Desbloqueaste «" + next.name() + "» en el árbol " + prog.name() + "!");
                    out.achievement("cal_primer_desbloqueo", "Primer escalón desbloqueado", "🔓");
                    String[] ach = STEP_ACHIEVEMENTS.get(next.movementKey());
                    if (ach != null) out.achievement(ach[0], ach[1], ach[2]);
                } else if (!mastered.contains(pk)) {
                    mastered.add(pk);
                    out.highlight("¡Dominaste el árbol " + prog.name() + "! Sigue con series de calidad o suma lastre.");
                    out.achievement("cal_arbol_dominado", "Árbol de habilidades dominado", "👑");
                }
                continue;
            }

            Prescription planned = sets.get(0).planned();
            int plannedMin = planned == null ? CalisthenicsRules.criteriaValue(crit) / 2
                    : hold ? nz(planned.holdS(), 20) : nz(planned.repsMin() != null ? planned.repsMin() : planned.reps(), 5);
            if (CalisthenicsRules.farBelow(plannedMin, logs, hold)) {
                int n = CalisthenicsRules.num(struggle.get(pk), 0) + 1;
                if (n >= 2 && current > 0) {
                    StepInfo prev = prog.step(current - 1);
                    out.step(pk, current - 1);
                    steps.put(pk, current - 1);
                    struggle.remove(pk);
                    holds.remove(pk);
                    regress = true;
                    out.highlight("Volvemos a «" + prev.name() + "» en " + prog.name()
                            + " para consolidar: dos sesiones seguidas lejos del objetivo. Retroceder un escalón también es progresar.");
                } else {
                    struggle.put(pk, n);
                    out.highlight("«" + step.name() + "» quedó lejos del objetivo. Si se repite, retrocederemos un escalón para consolidar.");
                }
                continue;
            }
            struggle.remove(pk);
            if (current < prog.lastIndex()) {
                int gap = CalisthenicsRules.gap(crit, logs);
                out.highlight("Te faltan " + gap + (hold ? " s" : " repeticiones") + " (sumando tus mejores series) para desbloquear «"
                        + prog.step(current + 1).name() + "»");
            }
            // Sostenimientos: si cumpliste todas las series del objetivo, el siguiente objetivo sube 5 s.
            if (hold && planned != null && planned.holdS() != null) {
                int plannedSets = planned.sets() == null ? logs.size() : planned.sets();
                boolean allDone = logs.size() >= plannedSets && logs.stream().allMatch(s -> s.value(true) >= planned.holdS());
                int nextHold = Math.min(CalisthenicsRules.criteriaValue(crit), planned.holdS() + 5);
                if (allDone && nextHold > planned.holdS()) {
                    holds.put(pk, Map.of("step", current, "s", nextHold));
                    applyHold(uw, step.movementKey(), pk, nextHold);
                    out.highlight("Sube a " + nextHold + " s en «" + step.name() + "» la próxima vez");
                }
            }
        }

        out.state("holds", holds).state("struggle", struggle).state("mastered", mastered);
        int unlocked = steps.entrySet().stream().filter(x -> x.getKey().startsWith("cal_")).mapToInt(Map.Entry::getValue).sum()
                + mastered.size();
        out.metric("cal_escalones", unlocked);
        if (regress) out.regeneratePlan();
        return out.build();
    }

    private void applyHold(UUID uw, String movementKey, String progKey, int seconds) {
        for (var block : plans.activeBlocksOf(uw, movementKey)) {
            if (progKey.equals(block.progressionKey())) plans.updatePrescription(block.id(), withHold(block.prescription(), seconds));
        }
    }

    private double best(UUID uw, String metric, String movement) {
        return metrics.seriesWhere(uw, metric, "movement", movement, 200).stream().mapToDouble(Widget.Point::value).max().orElse(0);
    }

    // ───────────────────────── Tests ─────────────────────────

    @Override
    public AssessmentOutcome onAssessment(WorldContext ctx, String key, double value) {
        String[] t = TESTS.get(key);
        if (t == null || value < 0) return AssessmentOutcome.none();
        String tree = t[0];
        String movement = t[1];
        boolean hold = "hold".equals(t[2]);
        int v = (int) Math.round(value);
        String name = kit.catalog().movement(movement).map(m -> m.name()).orElse(movement);
        SessionOutcome.Builder fx = SessionOutcome.builder();
        List<String> highlights = new ArrayList<>();
        if (v > 0) fx.metric(hold ? "cal_sostenimiento" : "cal_max_reps", v, Map.of("movement", movement, "name", name, "test", key));

        // Actualiza las marcas guardadas (para futuras ubicaciones).
        Map<String, Object> maxes = new LinkedHashMap<>(ctx.world().stateMap("maxes"));
        switch (key) {
            case "cal_test_flexiones" -> maxes.put("pushups", v);
            case "cal_test_dominadas" -> maxes.put("pullups", v);
            case "cal_test_fondos" -> maxes.put("dips", v);
            case "cal_test_sentadillas" -> maxes.put("squats", v);
            default -> {
            }
        }
        fx.state("maxes", maxes);

        ProgressionInfo prog = progression(tree);
        List<String> trees = treesOf(ctx);
        if (prog != null && trees.contains(tree)) {
            int current = userWorlds.progressionSteps(ctx.world().id()).getOrDefault(prog.key(), 0);
            int placed = Math.min(prog.lastIndex(), testPlacement(key, v, maxesOf(maxes), ctx));
            if (placed > current) {
                fx.step(prog.key(), placed).regeneratePlan();
                highlights.add("Tu test te adelanta en " + prog.name() + ": de «" + prog.step(current).name() + "» a «"
                        + prog.step(placed).name() + "»");
            } else {
                highlights.add(placed == current
                        ? prog.name() + ": el test confirma tu escalón «" + prog.step(current).name() + "»"
                        : prog.name() + ": sigues en «" + prog.step(current).name() + "» (un test nunca te baja de escalón)");
            }
        }
        highlights.add(0, name + ": " + v + (hold ? " s" : " repeticiones"));
        return new AssessmentOutcome(null, highlights, fx.build());
    }

    /** Escalón que justifica el test (nunca se usa para bajar: solo si supera al actual). */
    private int testPlacement(String key, int v, Maxes m, WorldContext ctx) {
        HealthSummary h = ctx.user().health();
        Flags flags = new Flags(false, h.hasLowerLimbIssue(), ctx.user().profile().ageOr(30) >= 65,
                ctx.world().profile().listWithoutNone("c_skills").contains("l_sit"));
        return switch (key) {
            case "cal_test_flexiones" -> CalisthenicsRules.pushPlacement(v, flags).step();
            case "cal_test_dominadas" -> CalisthenicsRules.pullPlacement(v, m.pushups()).step();
            case "cal_test_fondos" -> CalisthenicsRules.dipPlacement(v, m.pushups()).step();
            case "cal_test_sentadillas" -> CalisthenicsRules.legPlacement(v, flags).step();
            // Hollow: 3×30 s piden ~45 s de máximo para dar por superado el hollow hold.
            case "cal_test_hollow" -> v >= 45 ? 4 : v >= 20 ? 3 : v >= 10 ? 2 : 0;
            // Colgado: 45 s de agarre dan por superado el primer escalón del jale.
            case "cal_test_colgado" -> v >= 45 ? 1 : 0;
            default -> 0;
        };
    }

    // ───────────────────────── Dashboard ─────────────────────────

    @Override
    public List<Widget> dashboard(WorldContext ctx, PlanView plan) {
        UUID uw = ctx.world().id();
        Map<String, Integer> steps = userWorlds.progressionSteps(uw);
        List<String> mastered = stringList(ctx.world().stateValue("mastered"));
        List<Widget> w = new ArrayList<>();
        w.add(Widget.nextSession("Tu próxima sesión", plan.nextSession()));
        w.add(Widget.skillTree("arboles", "Tus árboles de habilidades", "Hecho · actual · bloqueado", trees(ctx, steps, mastered)));
        int unlocked = steps.values().stream().mapToInt(Integer::intValue).sum() + mastered.size();
        // Mejor serie registrada o, si aún no hay, la marca declarada en el onboarding o el último test.
        int bestPull = (int) Math.max(best(uw, "cal_max_reps", "dominadas"),
                CalisthenicsRules.num(ctx.world().stateMap("maxes").get("pullups"), 0));
        long done = plan.doneThisWeek();
        int total = plan.currentWeekSessions().size();
        w.add(Widget.statRow("resumen", "Tu calistenia", List.of(
                new Stat("Escalones", String.valueOf(unlocked), null, "desbloqueados", unlocked > 0 ? "good" : null),
                new Stat("Sesiones", done + "/" + total, null, "esta semana", total > 0 && done >= total ? "good" : null),
                new Stat("Dominadas", bestPull > 0 ? String.valueOf(bestPull) : "—", bestPull > 0 ? "reps" : null,
                        bestPull > 0 ? "mejor serie" : "aún sin dominada", bestPull > 0 ? "good" : null))));
        String lineMovement = best(uw, "cal_max_reps", "dominadas") > 0 ? "dominadas"
                : best(uw, "cal_max_reps", "flexiones") > 0 ? "flexiones"
                        : metrics.latestByContext(uw, "cal_max_reps", "movement").keySet().stream().findFirst().orElse(null);
        if (lineMovement != null) {
            String name = kit.catalog().movement(lineMovement).map(m -> m.name()).orElse(lineMovement);
            w.add(Widget.line("max_" + lineMovement, name + " por serie", "Mejor serie en sesiones y tests", "reps",
                    metrics.seriesWhere(uw, "cal_max_reps", "movement", lineMovement, 20), "up", null));
        }
        w.add(Widget.insights("Para ti", insights(ctx, steps, plan)));
        return w;
    }

    private List<Tree> trees(WorldContext ctx, Map<String, Integer> steps, List<String> mastered) {
        List<Tree> out = new ArrayList<>();
        for (String tree : treesOf(ctx)) {
            ProgressionInfo prog = progression(tree);
            if (prog == null) continue;
            int current = Math.min(steps.getOrDefault(prog.key(), 0), prog.lastIndex());
            boolean all = mastered.contains(prog.key());
            List<TreeStep> ts = new ArrayList<>();
            for (StepInfo s : prog.steps()) {
                String status = s.index() < current || (all && s.index() == current) ? "done"
                        : s.index() == current ? "current" : "locked";
                ts.add(new TreeStep(s.index(), s.name(), status));
            }
            out.add(new Tree(prog.key(), prog.name(), prog.icon(), current, prog.steps().size(), ts));
        }
        return out;
    }

    private List<Insight> insights(WorldContext ctx, Map<String, Integer> steps, PlanView plan) {
        List<Insight> out = new ArrayList<>();
        UUID uw = ctx.world().id();
        UserContext user = ctx.user();
        // 1) El escalón más cerca de desbloquearse según las últimas series registradas.
        String bestText = null;
        String firstGoal = null;
        int bestGap = Integer.MAX_VALUE;
        Set<String> equipment = equipmentFor(ctx);
        Set<String> usable = kit.usableMovements(KEY, user, equipment).stream().map(WorldMovement::key).collect(Collectors.toSet());
        Set<String> safe = kit.safety().filter(kit.catalog().movements(KEY), user).stream().map(WorldMovement::key)
                .collect(Collectors.toSet());
        Set<String> missingEquipment = new LinkedHashSet<>();
        for (String tree : treesOf(ctx)) {
            ProgressionInfo prog = progression(tree);
            if (prog == null) continue;
            int current = Math.min(steps.getOrDefault(prog.key(), 0), prog.lastIndex());
            StepInfo step = prog.step(current);
            if (!usable.contains(step.movementKey()) && safe.contains(step.movementKey())) {
                kit.catalog().movement(step.movementKey()).ifPresent(mi -> mi.equipment().stream()
                        .filter(e -> !equipment.contains(e)).forEach(missingEquipment::add));
            }
            if (current >= prog.lastIndex()) continue;
            Map<String, Object> crit = step.unlockCriteria();
            int n = CalisthenicsRules.criteriaSets(crit);
            List<SetLog> recent = sessionLogs.blocksOf(uw, step.movementKey(), n).stream().filter(LoggedBlock::completed)
                    .map(b -> new SetLog(b.reps(), b.holdS())).toList();
            if (firstGoal == null && usable.contains(step.movementKey())) {
                firstGoal = prog.name() + ": " + criteriaText(crit) + " en «" + step.name() + "» desbloquean «"
                        + prog.step(current + 1).name() + "».";
            }
            if (recent.isEmpty()) continue;
            int gap = CalisthenicsRules.gap(crit, recent);
            if (gap > 0 && gap < bestGap) {
                bestGap = gap;
                boolean hold = CalisthenicsRules.isHold(crit);
                bestText = "Te faltan " + gap + (hold ? " s" : gap == 1 ? " repetición" : " repeticiones") + " para desbloquear «"
                        + prog.step(current + 1).name() + "» (" + prog.name() + "). Criterio: " + criteriaText(crit) + ".";
            }
        }
        if (bestText != null) out.add(Insight.of("🔓", "Tu próximo desbloqueo", bestText, "good"));
        else if (firstGoal != null) out.add(Insight.of("🎯", "Tu próximo desbloqueo", firstGoal
                + " Registra cada serie y te diremos cuánto te falta.", "info"));
        // 2) Equipo que desbloquearía árboles enteros.
        if (!missingEquipment.isEmpty()) {
            out.add(Insight.of("🛠️", "Equipo que te abre camino", "Con " + missingEquipment.stream()
                    .map(e -> EQUIPMENT_LABELS.getOrDefault(e, e)).collect(Collectors.joining(" o "))
                    + " (en casa o en un parque) harías tu escalón real en vez del sustituto.", "info"));
        }
        // 3) Escalones con una sesión muy por debajo del objetivo.
        Map<String, Object> struggle = ctx.world().stateMap("struggle");
        if (!struggle.isEmpty()) {
            String names = struggle.keySet().stream().map(k -> treeName(treeOf(k))).collect(Collectors.joining(", "));
            out.add(Insight.of("🧱", "Consolida antes de avanzar", names + ": la última sesión quedó lejos del objetivo. Descansa bien; si se repite, bajamos un escalón.", "warn"));
        }
        // 4) Salud.
        HealthSummary h = user.health();
        if (!h.injuredZones().isEmpty()) {
            out.add(Insight.of("🩹", "Cuidamos tus zonas sensibles", "Evitamos cargar: " + String.join(", ", h.injuredZones())
                    + ". Si un ejercicio duele (no es esfuerzo, es dolor), detente.", "warn"));
        }
        if (plan.doneThisWeek() == 0) {
            out.add(new Insight("▶️", "Empieza la semana", "Tu primera sesión te espera.", "info", "Entrenar", "/session"));
        }
        return out;
    }

    @Override
    public List<Widget> progress(WorldContext ctx) {
        UUID uw = ctx.world().id();
        Map<String, Integer> steps = userWorlds.progressionSteps(uw);
        List<String> mastered = stringList(ctx.world().stateValue("mastered"));
        List<Widget> w = new ArrayList<>();
        List<Tree> trees = trees(ctx, steps, mastered);
        w.add(Widget.skillTree("arboles", "Árboles de habilidades", null, trees));
        w.add(Widget.bars("progreso_arboles", "Progreso por árbol", "Escalones superados sobre el total",
                trees.stream().map(t -> {
                    int doneSteps = (int) t.steps().stream().filter(s -> "done".equals(s.status())).count();
                    return new Widget.Bar(t.name(), Math.round(doneSteps * 100.0 / Math.max(1, t.total())),
                            doneSteps + "/" + t.total() + " · " + t.steps().get(t.current()).name());
                }).toList()));
        Map<String, Double> reps = metrics.latestByContext(uw, "cal_max_reps", "movement");
        List<String> keyMovements = new ArrayList<>(List.of("dominadas", "flexiones", "fondos_paralelas"));
        reps.keySet().stream().filter(k -> !keyMovements.contains(k)).forEach(keyMovements::add);
        keyMovements.stream().filter(reps::containsKey).limit(4).forEach(mk -> {
            String name = kit.catalog().movement(mk).map(m -> m.name()).orElse(mk);
            w.add(Widget.line("reps_" + mk, name, "Mejor serie (repeticiones)", "reps",
                    metrics.seriesWhere(uw, "cal_max_reps", "movement", mk, 30), "up", null));
        });
        Map<String, Double> holdsByMovement = metrics.latestByContext(uw, "cal_sostenimiento", "movement");
        holdsByMovement.keySet().stream().limit(2).forEach(mk -> {
            String name = kit.catalog().movement(mk).map(m -> m.name()).orElse(mk);
            w.add(Widget.line("hold_" + mk, name, "Mejor sostenimiento", "s",
                    metrics.seriesWhere(uw, "cal_sostenimiento", "movement", mk, 30), "up", null));
        });
        w.add(Widget.weekBars("sesiones", "Sesiones por semana", null, "sesiones",
                metrics.weekly(uw, "minutos_sesion", 8, "count"), (double) Math.max(2, Math.min(5, ctx.user().profile().daysOr(3)))));
        List<Widget.Point> unlockedSeries = metrics.series(uw, "cal_escalones", 30);
        if (!unlockedSeries.isEmpty()) {
            w.add(Widget.line("escalones", "Escalones desbloqueados", "Suma de todos tus árboles", "escalones", unlockedSeries, "up", null));
        }
        if (reps.isEmpty() && holdsByMovement.isEmpty()) {
            w.add(Widget.notice("sin_datos", "info", "Aún no hay marcas",
                    "Registra repeticiones y segundos de cada serie y aquí verás crecer tus habilidades."));
        }
        return w;
    }

    @Override
    public SessionUi sessionUi() {
        return new SessionUi("sets", List.of(), List.of("rpe"), List.of("reps", "hold"), List.of(
                "Solo cuentan las repeticiones limpias: recorrido completo y cuerpo en línea.",
                "Calidad antes que cantidad: si la técnica se rompe, termina la serie.",
                "En los sostenimientos respira corto y aprieta abdomen y glúteos.",
                "Dolor en muñeca, codo u hombro no es esfuerzo: detente y cambia de escalón."));
    }

    @Override
    public String assistantPersona(WorldContext ctx) {
        Map<String, Integer> steps = userWorlds.progressionSteps(ctx.world().id());
        StringBuilder trees = new StringBuilder();
        for (String tree : treesOf(ctx)) {
            ProgressionInfo prog = progression(tree);
            if (prog == null) continue;
            int current = Math.min(steps.getOrDefault(prog.key(), 0), prog.lastIndex());
            StepInfo s = prog.step(current);
            trees.append("- ").append(prog.name()).append(": escalón ").append(current + 1).append("/").append(prog.steps().size())
                    .append(" «").append(s.name()).append("»");
            if (current < prog.lastIndex()) {
                trees.append(", desbloquea «").append(prog.step(current + 1).name()).append("» con ")
                        .append(criteriaText(s.unlockCriteria()));
            }
            trees.append('\n');
        }
        return """
                Eres un entrenador de calistenia (entrenamiento con el peso corporal). Piensas en árboles de habilidades:
                cada ejercicio es un escalón y se desbloquea el siguiente al cumplir un criterio de repeticiones o segundos.
                El usuario sigue la ruta "%s" en nivel %d de 5. Sus árboles:
                %s
                Prioriza técnica limpia y recorrido completo sobre el número de repeticiones; sugiere variantes del mismo
                árbol (más fáciles o más difíciles) en lugar de ejercicios con pesas. Recomienda calentar muñecas y hombros,
                y nunca empujar a través del dolor articular. Si pregunta por un escalón, explica el criterio para desbloquearlo.
                """.formatted(ctx.world().trackKey(), ctx.world().level(), trees.toString().strip());
    }

    // ───────────────────────── Utilidades ─────────────────────────

    private ProgressionInfo progression(String tree) {
        return kit.catalog().progression(KEY, progressionKey(tree)).orElse(null);
    }

    private int stepOf(String tree, String movementKey, int fallback) {
        ProgressionInfo p = progression(tree);
        if (p == null) return fallback;
        return p.steps().stream().filter(s -> movementKey.equals(s.movementKey())).map(StepInfo::index).findFirst().orElse(fallback);
    }

    private String treeName(String tree) {
        ProgressionInfo p = progression(tree);
        return p == null ? tree : p.name();
    }

    private static String trackName(String track) {
        return switch (track) {
            case "fuerza_relativa" -> "Fuerza relativa";
            case "habilidades" -> "Habilidades";
            default -> "Fundamentos";
        };
    }

    private static List<String> stringList(Object o) {
        return o instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
    }

    private static int nz(Integer v, int fallback) {
        return v == null ? fallback : v;
    }
}
