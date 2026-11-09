package com.sportacus.backend.worlds.running;

import static com.sportacus.backend.world.form.Forms.number;
import static com.sportacus.backend.world.form.Forms.opt;
import static com.sportacus.backend.world.form.Forms.optional;
import static com.sportacus.backend.world.form.Forms.showIf;
import static com.sportacus.backend.world.form.Forms.single;
import static com.sportacus.backend.world.form.Forms.multi;
import static com.sportacus.backend.world.form.Forms.text;
import static com.sportacus.backend.worlds.running.RunningMath.km;
import static com.sportacus.backend.worlds.running.RunningMath.pace;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.catalog.CatalogModels.TrackInfo;
import com.sportacus.backend.world.catalog.CatalogModels.WorldMovement;
import com.sportacus.backend.world.core.AssessmentOutcome;
import com.sportacus.backend.world.core.DiscoveryInput;
import com.sportacus.backend.world.core.EnrollmentResult;
import com.sportacus.backend.world.core.EnrollmentResult.GoalSpec;
import com.sportacus.backend.world.core.Factor;
import com.sportacus.backend.world.core.GeneratedPlan;
import com.sportacus.backend.world.core.LoggedSession;
import com.sportacus.backend.world.core.LoggedSession.LoggedBlock;
import com.sportacus.backend.world.core.PlanView;
import com.sportacus.backend.world.core.PlanView.SessionView;
import com.sportacus.backend.world.core.Prescription;
import com.sportacus.backend.world.core.SessionOutcome;
import com.sportacus.backend.world.core.SessionUi;
import com.sportacus.backend.world.core.UserContext;
import com.sportacus.backend.world.core.Widget;
import com.sportacus.backend.world.core.Widget.Insight;
import com.sportacus.backend.world.core.Widget.Phase;
import com.sportacus.backend.world.core.Widget.Point;
import com.sportacus.backend.world.core.Widget.Stat;
import com.sportacus.backend.world.core.WorldContext;
import com.sportacus.backend.world.core.WorldModule;
import com.sportacus.backend.world.core.WorldToolkit;
import com.sportacus.backend.world.form.FormSpec;
import com.sportacus.backend.world.form.Forms;
import com.sportacus.backend.world.metrics.MetricRepository;
import com.sportacus.backend.world.plan.PlanRepository;
import com.sportacus.backend.worlds.running.RunningMath.Paces;
import com.sportacus.backend.worlds.running.RunningPlanBuilder.Mode;

/**
 * Mundo Running: plan periodizado por semanas hacia una meta (primer 5K, mejorar 5K, 10K, media maratón,
 * base aeróbica).
 * <ul>
 * <li>Ritmos desde el 5K (o 10K vía Riegel): suave rp+60…90, tempo rp+20, intervalos rp−3 s/km.</li>
 * <li>Volumen +≤10 %/semana, descarga cada 4 semanas, 80/20, tirada larga en el día preferido, afinamiento.</li>
 * <li>Alternativas seguras: caminar-correr (impacto medio) y caminata (bajo) cuando el núcleo restringe el impacto alto.</li>
 * <li>Métricas: km por sesión, ritmo, tirada larga, 5K estimado, Cooper y VO₂máx.</li>
 * </ul>
 */
@Component
public class RunningWorld implements WorldModule {

    public static final String KEY = "running";
    static final List<String> ABILITIES = List.of("no_corro", "corro_camino", "corro_30", "5k", "10k");
    static final int[] DEFAULT_KM = { 0, 5, 10, 15, 25 };
    static final int[] DEFAULT_EASY_PACE = { 480, 465, 435, 400, 365 };
    static final Set<String> CONTINUOUS_KINDS = Set.of("rodaje", "tirada_larga", "tempo", "prueba");
    static final List<String> PACED_MOVEMENTS = List.of("trote_suave", "tirada_larga", "carrera_tempo", "intervalos_carrera");

    private final WorldToolkit kit;
    private final MetricRepository metrics;
    private final PlanRepository plans;

    public RunningWorld(WorldToolkit kit, MetricRepository metrics, PlanRepository plans) {
        this.kit = kit;
        this.metrics = metrics;
        this.plans = plans;
    }

    @Override
    public String key() {
        return KEY;
    }

    // ───────────────────────── Onboarding ─────────────────────────

    @Override
    public FormSpec onboardingForm(UserContext user) {
        return Forms.form("running", "Tu plan de running", "Con esto calculamos tu volumen, tus ritmos y tu progresión.",
                single("r_ability", "¿Cómo corres hoy?", "Sé honesto: empezar por debajo es más seguro que por encima.",
                        opt("no_corro", "🚶", "No corro", "Camino, pero correr me cuesta"),
                        opt("corro_camino", "🚶‍♂️", "Alterno correr y caminar"),
                        opt("corro_30", "🏃", "Corro 20–30 minutos seguidos"),
                        opt("5k", "👟", "Corro 5 km sin parar"),
                        opt("10k", "🏅", "Corro 10 km o más")),
                optional(showIf(number("r_weekly_km", "¿Cuántos km corres a la semana ahora?",
                        "Un promedio de las últimas 4 semanas. Si no lo sabes, déjalo vacío.", 0, 150, "km"),
                        "r_ability", "corro_camino", "corro_30", "5k", "10k")),
                optional(showIf(number("r_5k_time", "Si lo sabes: ¿tu mejor tiempo reciente en 5 km?",
                        "En minutos (p. ej. 28). De los últimos 6 meses.", 12, 60, "min"),
                        "r_ability", "corro_30", "5k", "10k")),
                optional(showIf(number("r_10k_time", "¿Y en 10 km?", "En minutos. Si no tienes 5K, lo estimamos con este.",
                        25, 120, "min"), "r_ability", "5k", "10k")),
                single("r_goal", "¿Cuál es tu meta?", null,
                        opt("primer_5k", "🌱", "Mi primer 5K", "De caminar a correr 30 minutos"),
                        opt("mejorar_5k", "⚡", "Mejorar mi 5K"),
                        opt("10k", "🎯", "Correr 10K"),
                        opt("media_maraton", "🏅", "Media maratón", "Requiere correr 30 min seguidos"),
                        opt("base_aerobica", "🫀", "Correr por salud", "Sin carrera objetivo")),
                optional(showIf(text("r_race_date", "¿Tienes fecha de carrera?", "Formato AAAA-MM-DD. Ajustamos el plan para llegar afinado."),
                        "r_goal", "primer_5k", "mejorar_5k", "10k", "media_maraton")),
                single("r_long_day", "¿Qué día prefieres para la tirada larga?", "Es la sesión más larga de la semana.",
                        opt("6", "📅", "Sábado"), opt("7", "☀️", "Domingo"), opt("0", "🤷", "Me da igual")),
                optional(multi("r_surface", "¿Dónde vas a correr?", "Elige las que quieras.",
                        opt("asfalto", "🛣️", "Calle o asfalto"), opt("parque", "🌳", "Parque o tierra"),
                        opt("pista", "🏟️", "Pista de atletismo"), opt("cinta", "🏃", "Cinta de correr"),
                        opt("montana", "⛰️", "Montaña o senderos"))),
                optional(single("r_watch", "¿Con qué mides tus salidas?", "No es imprescindible: el plan funciona por tiempo.",
                        opt("gps", "⌚", "Reloj con GPS"), opt("movil", "📱", "App en el móvil"),
                        opt("nada", "⏱️", "Solo con el tiempo"))));
    }

    @Override
    public List<Factor> discoveryFactors(DiscoveryInput in) {
        List<Factor> f = new ArrayList<>();
        Answers a = in.answers();
        UserContext u = in.user();
        if (a.contains("d_motivation", "resistencia")) f.add(Factor.of("motivacion", 25, "Quieres ganar resistencia y despejarte: correr es la vía más directa"));
        if (a.contains("d_enjoy", "correr")) f.add(Factor.of("gusto", 15, "Te gusta correr o caminar rápido"));
        if (a.contains("d_enjoy", "aire_libre")) f.add(Factor.of("aire_libre", 6, "Disfrutas del aire libre"));
        if ("numeros".equals(a.string("d_motivator"))) f.add(Factor.of("motivador", 6, "Te motivan los números: km, ritmos y marcas"));
        if ("retos".equals(a.string("d_motivator"))) f.add(Factor.of("retos", 4, "Te gustan los retos: una carrera en el calendario es uno"));
        if (a.contains("d_experience", "running")) f.add(Factor.of("experiencia", 8, "Ya has corrido antes"));
        if (u.profile().hasEnvironment("exterior") || u.profile().hasEnvironment("parque")) {
            f.add(Factor.of("entorno", 6, "Tienes calles o parques donde correr"));
        }
        if ("solo".equals(a.string("d_social"))) f.add(Factor.of("social", 3, "Prefieres entrenar a tu ritmo"));
        if ("equipo".equals(a.string("d_social"))) f.add(Factor.of("social", -4, "Prefieres el juego en equipo"));
        HealthSummary h = u.health();
        if (h.hasLowerLimbIssue()) {
            f.add(Factor.of("lesion", -10, "Tienes molestias en piernas o pies: empezaríamos caminando, sin impacto de carrera"));
        }
        Double bmi = u.profile().bmi();
        if (bmi != null && bmi >= 35) f.add(Factor.of("imc", -6, "Para cuidar tus articulaciones empezaríamos alternando caminar y trotar"));
        if (h.mustRefer()) f.add(Factor.of("seguridad", -20, "Antes de correr conviene la valoración de un profesional"));
        if (u.profile().ageOr(30) >= 65) f.add(Factor.of("edad", -3, "Con la edad, progresión más gradual y sin esfuerzos explosivos"));
        return f;
    }

    @Override
    public EnrollmentResult enroll(UserContext user, Answers a) {
        HealthSummary h = user.health();
        String ability = a.string("r_ability", "corro_camino");
        int rank = Math.max(0, ABILITIES.indexOf(ability));
        Double declaredKm = a.number("r_weekly_km");
        double km = declaredKm != null && declaredKm >= 0 ? declaredKm : DEFAULT_KM[rank];
        Integer t5k = fiveKFromAnswers(a);
        String track = a.string("r_goal", rank >= 3 ? "mejorar_5k" : "primer_5k");
        List<Factor> why = new ArrayList<>();
        List<String> notices = new ArrayList<>();

        // Ruta coherente con la capacidad actual.
        if ("primer_5k".equals(track) && rank >= 3) {
            track = "mejorar_5k";
            why.add(Factor.of("ruta", 0, "Ya corres 5 km: pasamos a Mejorar mi 5K en lugar del plan de iniciación"));
            notices.add("Como ya corres 5 km, tu plan es Mejorar mi 5K.");
        } else if (rank < 2 && List.of("mejorar_5k", "10k", "media_maraton").contains(track)) {
            String wanted = track;
            track = "primer_5k";
            why.add(Factor.of("ruta", 0, "Para " + trackLabel(wanted) + " hace falta correr 30 minutos seguidos: empezamos por Mi primer 5K"));
            notices.add("Primero llegarás a correr 30 minutos seguidos (9 semanas); después podrás ir a por " + trackLabel(wanted) + ".");
        }
        if ("media_maraton".equals(track) && km < 15) {
            notices.add("Con menos de 15 km semanales, 12 semanas son justas para una media maratón: subiremos el volumen con prudencia.");
        }
        boolean severeLeg = h.hasLowerLimbIssue() && List.of("moderada", "grave").contains(String.valueOf(h.worstInjurySeverity()));
        if ((severeLeg || h.mustRefer()) && !List.of("primer_5k", "base_aerobica").contains(track)) {
            track = "base_aerobica";
            why.add(Factor.of("seguridad", 0, "Con tu estado de salud actual la meta de carrera se pospone: base aeróbica caminando"));
        }
        if (why.stream().noneMatch(x -> "ruta".equals(x.key()) || "seguridad".equals(x.key()))) {
            why.add(0, Factor.of("ruta", 0, "Ruta " + trackLabel(track) + " según tu meta"));
        }
        if (severeLeg) {
            notices.add("Por tus molestias en " + String.join(", ", legZones(h)) + " empezamos con caminata (sin impacto de carrera). "
                    + "Cuando el dolor ceda, vuelve a hacer el onboarding para pasar a caminar-correr.");
        } else if (kit.safety().highImpactRestricted(user)) {
            notices.add("Excluimos intervalos, cuestas y aceleraciones (alto impacto); los días de calidad serán rodajes con cambios suaves.");
        }
        if (!h.vigorousAllowed()) {
            notices.add("Mantendremos intensidad moderada (sin intervalos ni tests máximos) hasta que confirmes el aval de un profesional.");
        }

        int level = Math.min(5, new int[] { 1, 1, 2, 3, 4 }[rank] + (km >= 40 ? 1 : 0));
        why.add(Factor.of("nivel", 0, "Nivel " + level + " de 5 según cómo corres hoy" + (declaredKm != null ? " y tus " + km(km) + " km semanales" : "")));
        if (t5k != null) {
            Paces p = RunningMath.paces(t5k);
            why.add(Factor.of("ritmos", 0, "5K de referencia " + RunningMath.time(t5k) + (a.number("r_5k_time") == null ? " (estimado desde tu 10K con Riegel)" : "")
                    + ": suave " + p.easyRange()));
        }

        // Duración del programa y fecha de carrera.
        TrackInfo info = kit.catalog().track(KEY, track).orElseThrow();
        int trackWeeks = info.configInt("weeks", 8);
        int planWeeks = trackWeeks;
        LocalDate today = LocalDate.now(MetricRepository.ZONE);
        LocalDate race = parseDate(a.string("r_race_date"));
        if (a.has("r_race_date") && race == null) notices.add("No entendimos la fecha de carrera (usa AAAA-MM-DD); el plan dura " + trackWeeks + " semanas.");
        if (race != null && !race.isAfter(today)) {
            notices.add("La fecha de carrera ya pasó; el plan dura " + trackWeeks + " semanas.");
            race = null;
        }
        if (race != null && RunningPlanBuilder.raceTrack(track)) {
            int until = (int) Math.ceil(ChronoUnit.DAYS.between(today, race) / 7.0);
            if (until < trackWeeks) {
                planWeeks = Math.max(2, until);
                notices.add("Tu carrera es en " + until + " semanas: comprimimos el plan a " + planWeeks + " semanas (menos subida de volumen).");
            } else if (until > trackWeeks) {
                planWeeks = Math.min(until, trackWeeks + 8);
                why.add(Factor.of("calendario", 0, "Tu carrera es en " + until + " semanas: añadimos " + (planWeeks - trackWeeks) + (planWeeks - trackWeeks == 1 ? " semana" : " semanas") + " de base"));
                if (until > planWeeks) notices.add("Tu carrera es en " + until + " semanas: el plan dura " + planWeeks + " y después pasa a mantenimiento.");
            }
        } else if (race != null && "primer_5k".equals(track)) {
            int until = (int) Math.ceil(ChronoUnit.DAYS.between(today, race) / 7.0);
            if (until < trackWeeks) notices.add("Tu carrera llega antes de terminar las 9 semanas: puedes completarla alternando caminar y correr.");
        }
        LocalDate target = race != null ? race : today.plusWeeks(planWeeks);

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("planStart", today.toString());
        state.put("planWeeks", planWeeks);
        if (t5k != null) state.put("t5kS", t5k);
        state.put("weeklyKm0", km);
        state.put("easyPaceDefault", DEFAULT_EASY_PACE[rank]);
        state.put("ability", ability);
        if (race != null) state.put("raceDate", race.toString());

        List<GoalSpec> goals = new ArrayList<>();
        switch (track) {
            case "primer_5k" -> goals.add(new GoalSpec("run_30min", "Correr 30 minutos (o 5 km) seguidos",
                    Map.of("minutes", 30, "km", 5), target));
            case "mejorar_5k" -> {
                Map<String, Object> t = new LinkedHashMap<>(Map.of("metric", "run_5k_tiempo_s"));
                String label = "Bajar tu marca de 5 km";
                if (t5k != null) {
                    int goal = (int) Math.round(t5k * 0.96);
                    t.put("value", goal);
                    label = "Bajar tu 5K de " + RunningMath.time(t5k) + " a " + RunningMath.time(goal);
                }
                goals.add(new GoalSpec("run_mejorar_5k", label, t, target));
            }
            case "10k" -> goals.add(new GoalSpec("run_10k", "Correr 10 km", Map.of("km", 10), target));
            case "media_maraton" -> goals.add(new GoalSpec("run_media", "Completar una media maratón (21,1 km)", Map.of("km", 21.1), target));
            default -> goals.add(new GoalSpec("run_base", "Sumar 150 minutos semanales de actividad aeróbica (OMS)",
                    Map.of("minutesPerWeek", 150), target));
        }
        if (a.contains("r_surface", "cinta")) notices.add("En cinta, pon un 1 % de inclinación para que el esfuerzo se parezca al de la calle.");
        return new EnrollmentResult(track, level, a.asMap(), state, goals, Map.of(), why, notices);
    }

    static Integer fiveKFromAnswers(Answers a) {
        Double t5 = a.number("r_5k_time");
        if (t5 != null && t5 >= 12 && t5 <= 60) return (int) Math.round(t5 * 60);
        Double t10 = a.number("r_10k_time");
        if (t10 != null && t10 >= 25 && t10 <= 120) return (int) Math.round(RunningMath.riegel(t10 * 60, 10000, 5000));
        return null;
    }

    static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        for (String f : List.of("yyyy-MM-dd", "d/M/yyyy", "d-M-yyyy")) {
            try {
                return LocalDate.parse(s.trim(), DateTimeFormatter.ofPattern(f));
            } catch (RuntimeException ignored) {
                // siguiente formato
            }
        }
        return null;
    }

    static String trackLabel(String track) {
        return switch (track) {
            case "primer_5k" -> "Mi primer 5K";
            case "mejorar_5k" -> "Mejorar mi 5K";
            case "10k" -> "10K";
            case "media_maraton" -> "la media maratón";
            default -> "Base aeróbica";
        };
    }

    private static List<String> legZones(HealthSummary h) {
        return h.injuredZones().stream().filter(z -> List.of("rodilla", "cadera", "tobillo", "pie").contains(z)).toList();
    }

    // ───────────────────────── Plan ─────────────────────────

    /** Parámetros derivados del contexto que comparten el plan, el dashboard y el asistente. */
    record Setup(TrackInfo track, Mode mode, int totalWeeks, LocalDate planStart, Paces paces, int defaultEasy,
            Set<String> allowed, boolean quality, boolean easyChanges, String restriction) {
    }

    Setup setup(WorldContext ctx) {
        UserContext user = ctx.user();
        HealthSummary h = user.health();
        String trackKey = ctx.world().trackKey() == null ? "base_aerobica" : ctx.world().trackKey();
        TrackInfo track = kit.catalog().track(KEY, trackKey).orElseThrow();
        Set<String> allowed = kit.usableMovements(KEY, user).stream().map(WorldMovement::key).collect(Collectors.toSet());
        String ability = ctx.world().stateString("ability");
        if (ability == null) ability = ctx.world().profile().string("r_ability", "corro_camino");
        boolean beginner = List.of("no_corro", "corro_camino").contains(ability);
        boolean severeLeg = h.hasLowerLimbIssue() && List.of("moderada", "grave").contains(String.valueOf(h.worstInjurySeverity()));
        Mode mode;
        String restriction = null;
        if (h.mustRefer() || severeLeg || !(allowed.contains("caminar_trotar") || allowed.contains("trote_suave"))) {
            mode = Mode.WALK;
            restriction = h.mustRefer() ? "Solo caminata mientras no tengas la valoración de un profesional"
                    : severeLeg ? "Plan de caminata (bajo impacto) por tus molestias en " + String.join(", ", legZones(h))
                            : "Plan de caminata (bajo impacto) por tu estado de salud";
        } else if ("primer_5k".equals(trackKey) || ("base_aerobica".equals(trackKey) && beginner) || !allowed.contains("trote_suave")) {
            mode = Mode.WALK_RUN;
        } else {
            mode = Mode.RUN;
        }
        boolean quality = mode == Mode.RUN && h.vigorousAllowed() && allowed.contains("intervalos_carrera");
        boolean easyChanges = mode == Mode.RUN && !quality && h.vigorousAllowed();
        if (mode != Mode.WALK && restriction == null) {
            if (!h.vigorousAllowed()) restriction = "Intensidad moderada hasta que confirmes el aval médico: sin intervalos ni cuestas";
            else if (kit.safety().highImpactRestricted(user)) restriction = "Sin intervalos, cuestas ni aceleraciones (alto impacto) por "
                    + impactReason(user) + "; caminar-correr y el rodaje suave (impacto medio) sí";
        }
        Integer weeksState = ctx.world().stateNumber("planWeeks") == null ? null : ctx.world().stateNumber("planWeeks").intValue();
        int total = weeksState != null ? weeksState : track.configInt("weeks", 8);
        if (mode == Mode.WALK_RUN) total = "base_aerobica".equals(trackKey) ? Math.min(6, total) : Math.min(9, total);
        LocalDate start = parseDate(ctx.world().stateString("planStart"));
        if (start == null) start = LocalDate.now(MetricRepository.ZONE);
        Double t5k = ctx.world().stateNumber("t5kS");
        Paces paces = t5k == null ? null : RunningMath.paces(t5k);
        Double de = ctx.world().stateNumber("easyPaceDefault");
        int defaultEasy = paces != null ? paces.easy() : de != null ? de.intValue() : 435;
        return new Setup(track, mode, total, start, paces, defaultEasy, allowed, quality, easyChanges, restriction);
    }

    private static String impactReason(UserContext u) {
        HealthSummary h = u.health();
        Double bmi = u.profile().bmi();
        if (h.hasLowerLimbIssue()) return "tus molestias en " + String.join(", ", legZones(h));
        if (h.has("embarazo")) return "el embarazo";
        if (h.hasAny("osteoporosis", "postoperatorio")) return "tu estado de salud";
        if (u.profile().ageOr(30) >= 65) return "edad (65+)";
        if (bmi != null && bmi >= 35) return "IMC ≥ 35";
        return "seguridad";
    }

    static int sessionsCap(String track) {
        return switch (track) {
            case "primer_5k" -> 3;
            case "media_maraton" -> 5;
            default -> 4;
        };
    }

    static int programWeek(LocalDate planStart, LocalDate day) {
        return (int) Math.max(0, ChronoUnit.DAYS.between(planStart, day) / 7);
    }

    @Override
    public GeneratedPlan generatePlan(WorldContext ctx) {
        return buildOutput(ctx).plan();
    }

    RunningPlanBuilder.Output buildOutput(WorldContext ctx) {
        Setup s = setup(ctx);
        UserContext user = ctx.user();
        String trackKey = s.track().key();
        int n = Math.max(2, Math.min(user.profile().daysOr(3), sessionsCap(trackKey)));
        if (s.mode() == Mode.WALK_RUN) n = 3;
        String longPref = ctx.world().profile().string("r_long_day", "0");
        Integer longDay = "6".equals(longPref) ? Integer.valueOf(6) : "7".equals(longPref) ? Integer.valueOf(7) : null;
        List<Integer> days = RunningPlanBuilder.withLongDay(kit.weekdays(user.profile(), n), s.mode() == Mode.RUN ? longDay : null);
        Double km0 = ctx.world().stateNumber("weeklyKm0");
        String watch = ctx.world().profile().string("r_watch", "nada");
        Object targetKm = s.track().config("targetKm");
        int startWeek = programWeek(s.planStart(), LocalDate.now(MetricRepository.ZONE));
        RunningPlanBuilder.Input in = new RunningPlanBuilder.Input(trackKey, s.track().name(), s.mode(), s.totalWeeks(),
                startWeek, n, days, longDay, Math.max(6, km0 == null ? 10 : km0), s.paces(), s.defaultEasy(), s.quality(),
                s.easyChanges(), !"nada".equals(watch), s.allowed(),
                targetKm instanceof Number num ? num.doubleValue() : null, user.profile().minutesOr(45), s.restriction());
        return RunningPlanBuilder.build(in);
    }

    // ───────────────────────── Evaluación ─────────────────────────

    @Override
    public SessionOutcome evaluateSession(WorldContext ctx, LoggedSession s) {
        SessionOutcome.Builder out = SessionOutcome.builder();
        UUID uw = ctx.world().id();
        Double meters = s.distanceM();
        if (meters == null || meters <= 0) {
            double sum = s.blocks().stream().filter(LoggedBlock::completed).map(LoggedBlock::distanceM)
                    .filter(d -> d != null && d > 0).mapToDouble(Double::doubleValue).sum();
            meters = sum > 0 ? sum : null;
        }
        if (meters == null) {
            out.highlight("Registra la distancia para ver tu ritmo y tus km semanales.");
            return out.build();
        }
        double km = Math.round(meters / 10.0) / 100.0;
        String kind = s.sessionKind();
        double prevLongest = metrics.best(uw, "run_km_sesion", true).orElse(0.0);
        out.metric("run_km_sesion", km, kind == null ? Map.of() : Map.of("kind", kind));
        if ("tirada_larga".equals(kind)) out.metric("run_tirada_larga", km);
        if (prevLongest > 0 && km > prevLongest) out.highlight("¡Tu salida más larga! " + km(km) + " km");

        Integer pace = null;
        if (s.durationMin() != null && km >= 1) {
            int p = (int) Math.round(s.durationMin() * 60 / km);
            if (p >= 150 && p <= 1200) {
                pace = p;
                Double prevBest = metrics.count(uw, "run_ritmo") > 0 ? metrics.best(uw, "run_ritmo", false).orElse(null) : null;
                out.metric("run_ritmo", p);
                if (prevBest != null && p < prevBest && km >= 3) out.highlight("Tu ritmo medio más rápido: " + pace(p) + " /km");
                else out.highlight("Ritmo medio: " + pace(p) + " /km");
            }
        }

        // Kilómetros de la semana frente a lo planificado y regla del 10 %.
        List<Widget.Bar> weeks = metrics.weekly(uw, "run_km_sesion", 4, "sum");
        double weekKm = weeks.get(weeks.size() - 1).value() + km;
        double planned = plannedKmThisWeek(ctx);
        if (planned > 0) {
            out.highlight(weekKm >= planned * 0.95 ? "¡Objetivo semanal cumplido! " + km(weekKm) + " km"
                    : "Esta semana: " + km(weekKm) + " de " + km(planned) + " km planificados");
        }
        double recentMax = weeks.subList(0, weeks.size() - 1).stream().mapToDouble(Widget.Bar::value).max().orElse(0);
        if (recentMax > 0 && weekKm > recentMax * 1.1 && weekKm - recentMax >= 2) {
            out.highlight("Llevas un " + Math.round((weekKm / recentMax - 1) * 100)
                    + " % más de km que tu semana más alta reciente: el resto de la semana, suave.");
        }

        // 5K estimado desde un esfuerzo continuo exigente (Riegel).
        Double t5kState = ctx.world().stateNumber("t5kS");
        if (pace != null && km >= 3 && s.rpe() != null && s.rpe() >= 8 && (kind == null || CONTINUOUS_KINDS.contains(kind))) {
            double est = RunningMath.riegel(s.durationMin() * 60.0, km * 1000, 5000);
            if (est >= 600 && est <= 4800) {
                out.metric("run_5k_estimado", Math.round(est));
                if (t5kState == null || est < t5kState * 0.98) {
                    Paces p = RunningMath.paces(est);
                    out.state("t5kS", (int) Math.round(est));
                    applyPaces(ctx, p);
                    out.highlight("5K estimado: " + RunningMath.time(est) + ". Nuevos ritmos: suave " + p.easyRange()
                            + ", tempo " + pace(p.tempo()) + " /km");
                }
            }
        }

        // Logros.
        boolean continuous = kind == null || CONTINUOUS_KINDS.contains(kind);
        if (continuous && km >= 4.95) out.achievement("run_primeros_5k", "Primeros 5 km sin parar", "🏃");
        if (continuous && km >= 9.95) out.achievement("run_primeros_10k", "Primeros 10 km", "🎯");
        if (continuous && km >= 21.0) out.achievement("run_media_maraton", "Media maratón completada", "🏅");
        boolean thirty = (s.durationMin() == null || s.durationMin() >= 30) && s.blocks().stream().anyMatch(b -> b.completed() && b.planned() != null
                && "time".equals(b.planned().type()) && (b.durationS() != null ? b.durationS() : b.planned().durationS() == null ? 0 : b.planned().durationS()) >= 1800
                && List.of("trote_suave", "tirada_larga").contains(b.movementKey()));
        if (thirty) out.achievement("run_30_min", "30 minutos corriendo sin parar", "⏱️");
        double total = metrics.sumLastDays(uw, "run_km_sesion", 36500) + km;
        if (total >= 100) out.achievement("run_100km", "100 km acumulados", "💯");
        int lvl = ctx.world().level();
        int byDistance = continuous ? (km >= 21 ? 5 : km >= 9.95 ? 4 : km >= 4.95 ? 3 : 0) : 0;
        if (byDistance > lvl) out.level(byDistance);
        return out.build();
    }

    /** Km planificados de la semana en curso del plan activo. */
    double plannedKmThisWeek(WorldContext ctx) {
        var plan = plans.active(ctx.world().id()).orElse(null);
        if (plan == null) return 0;
        long days = Math.max(0, ChronoUnit.DAYS.between(plan.startsOn(), LocalDate.now(MetricRepository.ZONE)));
        int week = plan.weeks() <= 1 ? 0 : (int) Math.min(plan.weeks() - 1, days / 7);
        int easy = setupEasy(ctx);
        var blocks = plans.blocksByPlan(plan.id());
        double sum = 0;
        for (var s : plans.sessions(plan.id())) {
            if (s.weekIndex() != week) continue;
            for (var b : blocks.getOrDefault(s.id(), List.of())) {
                sum += RunningPlanBuilder.blockKm(b.movementKey(), b.blockType(), b.prescription(), easy);
            }
        }
        return Math.round(sum * 10) / 10.0;
    }

    private static int setupEasy(WorldContext ctx) {
        Double t5k = ctx.world().stateNumber("t5kS");
        if (t5k != null) return RunningMath.paces(t5k).easy();
        Double de = ctx.world().stateNumber("easyPaceDefault");
        return de == null ? 435 : de.intValue();
    }

    static double plannedKm(List<SessionView> sessions, int easy) {
        double sum = 0;
        for (SessionView s : sessions) {
            for (var b : s.blocks()) sum += RunningPlanBuilder.blockKm(b.movementKey(), b.blockType(), b.prescription(), easy);
        }
        return Math.round(sum * 10) / 10.0;
    }

    /** Actualiza en sitio los ritmos de los bloques del plan activo (sin regenerar: se conserva lo hecho en la semana). */
    void applyPaces(WorldContext ctx, Paces p) {
        Double raceKm = kit.catalog().track(KEY, String.valueOf(ctx.world().trackKey()))
                .map(t -> t.config("targetKm") instanceof Number n ? n.doubleValue() : null).orElse(null);
        int racePace = raceKm == null ? p.race() : raceKm >= 21 ? p.half() : raceKm >= 10 ? p.tenK() : p.race();
        for (String movement : PACED_MOVEMENTS) {
            for (var block : plans.activeBlocksOf(ctx.world().id(), movement)) {
                Prescription pr = block.prescription();
                if (pr == null) continue;
                Prescription next = switch (movement) {
                    case "trote_suave" -> "time".equals(pr.type()) ? pr.withPace(p.easy()).withIntensity("suave " + p.easyRange()) : pr;
                    case "tirada_larga" -> pr.withPace(p.longRun()).withIntensity("suave " + p.easyRange());
                    case "carrera_tempo" -> "distance".equals(pr.type())
                            ? pr.withPace(racePace).withIntensity("ritmo de carrera " + pace(racePace) + " /km")
                            : pr.withPace(p.tempo()).withIntensity("tempo " + pace(p.tempo()) + " /km (RPE 7)");
                    case "intervalos_carrera" -> {
                        int ip = pr.distanceM() != null && pr.distanceM() >= 1600 ? p.race() + 8 : p.interval();
                        Integer work = pr.distanceM() == null ? pr.workS()
                                : Math.max(15, Math.round(pr.distanceM() / 1000f * ip / 15f) * 15);
                        yield new Prescription(pr.type(), pr.sets(), pr.reps(), pr.repsMin(), pr.repsMax(), pr.loadKg(),
                                pr.rir(), pr.holdS(), pr.durationS(), pr.distanceM(), pr.repeats(), work, pr.recoverS(),
                                pr.restS(), ip, "intervalo " + pace(ip) + " /km (RPE 8)", pr.tempo(), pr.painMax(),
                                pr.side(), pr.cue());
                    }
                    default -> pr;
                };
                if (next != pr) plans.updatePrescription(block.id(), next);
            }
        }
    }

    @Override
    public AssessmentOutcome onAssessment(WorldContext ctx, String key, double value) {
        List<String> hl = new ArrayList<>();
        SessionOutcome.Builder fx = SessionOutcome.builder();
        double fiveK;
        if ("run_cooper".equals(key)) {
            if (value < 600 || value > 5500) return AssessmentOutcome.none();
            double vo2 = Math.round(RunningMath.cooperVo2(value) * 10) / 10.0;
            fiveK = RunningMath.fiveKFromCooper(value);
            fx.metric("run_vo2max", vo2);
            hl.add("VO₂máx estimado: " + String.format(Locale.ROOT, "%.1f", vo2).replace('.', ',') + " ml/kg/min");
            hl.add("5K estimado: " + RunningMath.time(fiveK));
        } else if ("run_5k_contrarreloj".equals(key)) {
            if (value < 600 || value > 5400) return AssessmentOutcome.none();
            fiveK = value;
            Double prev = ctx.world().stateNumber("t5kS");
            hl.add("5K en " + RunningMath.time(value) + " (" + RunningMath.pace((int) Math.round(value / 5)) + " /km)"
                    + (prev != null && value < prev ? ": ¡mejoras " + RunningMath.time(prev - value) + "!" : ""));
        } else {
            return AssessmentOutcome.none();
        }
        Paces p = RunningMath.paces(fiveK);
        fx.metric("run_5k_estimado", Math.round(fiveK)).state("t5kS", (int) Math.round(fiveK));
        applyPaces(ctx, p);
        hl.add("Tus ritmos se actualizaron en el plan: suave " + p.easyRange() + ", tempo " + pace(p.tempo())
                + ", intervalos " + pace(p.interval()) + " /km");
        hl.add("Predicción (Riegel): 10K " + RunningMath.time(RunningMath.riegel(fiveK, 5000, 10000)) + " · media maratón "
                + RunningMath.time(RunningMath.riegel(fiveK, 5000, RunningMath.HALF_MARATHON_M)));
        return new AssessmentOutcome(null, hl, fx.build());
    }

    // ───────────────────────── Dashboard ─────────────────────────

    @Override
    public List<Widget> dashboard(WorldContext ctx, PlanView plan) {
        Setup s = setup(ctx);
        UUID uw = ctx.world().id();
        List<Widget> w = new ArrayList<>();
        w.add(Widget.nextSession("Tu próxima sesión", plan.nextSession()));
        double planned = plannedKm(plan.currentWeekSessions(), s.defaultEasy());
        List<Widget.Bar> bars = metrics.weekly(uw, "run_km_sesion", 8, "sum");
        double weekKm = bars.get(bars.size() - 1).value();
        w.add(Widget.weekBars("km_semana", "Kilómetros por semana", planned > 0 ? "Plan de esta semana: " + km(planned) + " km" : null,
                "km", bars, planned > 0 ? planned : null));
        List<Point> pacePts = metrics.series(uw, "run_ritmo", 20);
        double avgPace = pacePts.stream().skip(Math.max(0, pacePts.size() - 5)).mapToDouble(Point::value).average().orElse(0);
        double longest = metrics.best(uw, "run_km_sesion", true).orElse(0.0);
        w.add(Widget.statRow("semana", "Esta semana", List.of(
                new Stat("Km", km(weekKm) + (planned > 0 ? "/" + km(planned) : ""), "km", null,
                        planned > 0 && weekKm >= planned * 0.95 ? "good" : null),
                new Stat("Sesiones", plan.doneThisWeek() + "/" + plan.currentWeekSessions().size(), null, null,
                        plan.doneThisWeek() >= plan.currentWeekSessions().size() ? "good" : null),
                new Stat("Ritmo medio", avgPace > 0 ? pace((int) Math.round(avgPace)) : "—", "/km", "últimas 5 salidas", null),
                new Stat("Más larga", longest > 0 ? km(longest) : "—", "km", null, null))));

        int pw = programWeekOf(s, plan);
        boolean done = pw >= s.totalWeeks();
        LocalDate targetDate = parseDate(ctx.world().stateString("raceDate"));
        if (targetDate == null) targetDate = s.planStart().plusWeeks(s.totalWeeks());
        int shown = Math.min(pw + 1, s.totalWeeks());
        w.add(Widget.goal("meta", goalTitle(s.track().key()), s.track().name(),
                done ? 100 : Math.round(shown * 1000.0 / s.totalWeeks()) / 10.0,
                done ? "Programa completado: elige una nueva meta" : "Semana " + shown + " de " + s.totalWeeks(), targetDate));
        if (s.mode() == Mode.RUN && !done) {
            w.add(Widget.phaseTrack("fases", "Fases del plan", null, phases(s, pw)));
        }
        if (pacePts.size() >= 2) {
            w.add(Widget.line("ritmo", "Tendencia de ritmo", "Ritmo medio por salida", "s/km", pacePts, "down",
                    s.paces() == null ? null : (double) s.paces().easy()));
        }
        w.add(Widget.insights("Para ti", insights(ctx, s, plan, pw, bars)));
        return w;
    }

    /** Semana de programa = la del plan guardado + las semanas que ya habían pasado al generarlo. */
    static int programWeekOf(Setup s, PlanView plan) {
        int offset = plan.startsOn() == null ? 0 : programWeek(s.planStart(), plan.startsOn());
        return offset + plan.currentWeek();
    }

    private static String goalTitle(String track) {
        return switch (track) {
            case "primer_5k" -> "Camino a tus 30 minutos";
            case "mejorar_5k" -> "Camino a tu mejor 5K";
            case "10k" -> "Camino a tu 10K";
            case "media_maraton" -> "Camino a tu media maratón";
            default -> "Tu base aeróbica";
        };
    }

    private static List<Phase> phases(Setup s, int pw) {
        String track = s.track().key();
        List<Phase> out = new ArrayList<>();
        String current = RunningPlanBuilder.phaseName(Math.min(pw, s.totalWeeks() - 1), s.totalWeeks(), track);
        List<String> names = RunningPlanBuilder.raceTrack(track) ? List.of("Base", "Desarrollo", "Específico", "Afinamiento")
                : List.of("Base", "Desarrollo", "Consolidación");
        int ci = names.indexOf(current);
        String[] detail = { "Rodajes suaves y técnica", "Más volumen y primeras sesiones de calidad",
                RunningPlanBuilder.raceTrack(track) ? "Ritmos de carrera y tirada larga máxima" : "Sostener el volumen",
                "Menos volumen para llegar fresco" };
        for (int i = 0; i < names.size(); i++) {
            out.add(new Phase(names.get(i), i < ci ? "done" : i == ci ? "current" : "locked", detail[Math.min(i, detail.length - 1)]));
        }
        return out;
    }

    private List<Insight> insights(WorldContext ctx, Setup s, PlanView plan, int pw, List<Widget.Bar> bars) {
        List<Insight> out = new ArrayList<>();
        HealthSummary h = ctx.user().health();
        if (s.restriction() != null) out.add(Insight.of("🩹", "Plan adaptado a tu salud", s.restriction() + ".", "info"));
        double thisWeek = bars.get(bars.size() - 1).value();
        double recentMax = bars.subList(Math.max(0, bars.size() - 4), bars.size() - 1).stream().mapToDouble(Widget.Bar::value)
                .max().orElse(0);
        if (recentMax > 0 && thisWeek > recentMax * 1.1 && thisWeek - recentMax >= 2) {
            out.add(Insight.of("⚠️", "Cuidado con la regla del 10 %", "Esta semana llevas " + km(thisWeek) + " km, un "
                    + Math.round((thisWeek / recentMax - 1) * 100) + " % más que tu semana más alta reciente. Subir rápido el volumen es la causa más común de lesión: mantén el resto suave.", "warn"));
        }
        if (s.mode() == Mode.RUN && pw < s.totalWeeks() && (pw + 1) % 4 == 0
                && !(RunningPlanBuilder.raceTrack(s.track().key()) && RunningPlanBuilder.taperFactor(pw, s.totalWeeks(), s.track().key()) < 1)) {
            out.add(Insight.of("🛌", "Semana de descarga", "Esta semana baja el volumen (~25 %) para que el cuerpo asimile. No la conviertas en una semana normal.", "info"));
        }
        if (RunningPlanBuilder.raceTrack(s.track().key()) && pw < s.totalWeeks()
                && RunningPlanBuilder.taperFactor(pw, s.totalWeeks(), s.track().key()) < 1) {
            out.add(Insight.of("🎯", "Afinamiento", "Menos volumen y algo de ritmo: llegarás fresco a la carrera. Duerme bien y no estrenes zapatillas.", "info"));
        }
        plan.currentWeekSessions().stream().filter(x -> !x.done() && "tirada_larga".equals(x.kind())).findFirst()
                .ifPresent(x -> out.add(Insight.of("🛣️", "Próxima tirada larga",
                        x.title() + (x.weekday() == null ? "" : " el " + WorldToolkit.dayName(x.weekday()).toLowerCase())
                                + ". Siempre a ritmo suave.", "info")));
        if (s.paces() != null) {
            out.add(Insight.of("⏱️", "Tus ritmos", "Suave " + s.paces().easyRange() + " · tempo " + pace(s.paces().tempo())
                    + " · intervalos " + pace(s.paces().interval()) + " /km (5K de " + RunningMath.time(s.paces().fiveKS()) + ").", "info"));
        } else if (s.mode() == Mode.RUN && h.vigorousAllowed()) {
            out.add(new Insight("📏", "Calcula tus ritmos", "Haz el 5K contrarreloj o el test de Cooper y ajustaremos los ritmos de todo el plan.",
                    "info", "Ir a tests", "/tests"));
        }
        if (s.mode() == Mode.WALK_RUN && pw < s.totalWeeks()) {
            out.add(Insight.of("🐢", "Trota más lento de lo que crees", "En los tramos de trote debes poder hablar. Si una semana se te hace muy dura, repítela antes de avanzar.", "info"));
        }
        if (pw >= s.totalWeeks()) {
            out.add(Insight.of("🏁", "¡Programa completado!", "Estás en mantenimiento. Elige una nueva meta repitiendo el onboarding de Running.", "good"));
        }
        if (plan.doneThisWeek() == 0 && plan.nextSession() != null) {
            out.add(new Insight("▶️", "Empieza la semana", plan.nextSession().title() + " te espera.", "info", "Entrenar", "/session"));
        }
        if (out.isEmpty()) out.add(Insight.of("✅", "Todo en orden", "Registra distancia y tiempo para seguir tu ritmo.", "good"));
        return out;
    }

    @Override
    public List<Widget> progress(WorldContext ctx) {
        UUID uw = ctx.world().id();
        List<Widget> w = new ArrayList<>();
        w.add(Widget.weekBars("km_semana", "Kilómetros por semana", "Últimas 12 semanas", "km",
                metrics.weekly(uw, "run_km_sesion", 12, "sum"), null));
        w.add(Widget.line("ritmo", "Ritmo medio", "Por salida (menos es mejor)", "s/km", metrics.series(uw, "run_ritmo", 40), "down", null));
        w.add(Widget.line("tirada_larga", "Tirada larga", "Km de cada tirada larga", "km", metrics.series(uw, "run_tirada_larga", 30), "up", null));
        List<Point> est = metrics.series(uw, "run_5k_estimado", 30);
        if (!est.isEmpty()) w.add(Widget.line("5k_estimado", "5K estimado", "Riegel desde tests y esfuerzos continuos", "s", est, "down", null));
        List<Point> vo2 = metrics.series(uw, "run_vo2max", 30);
        if (!vo2.isEmpty()) w.add(Widget.line("vo2max", "VO₂máx estimado", "Test de Cooper", "ml/kg/min", vo2, "up", null));
        double longest = metrics.best(uw, "run_km_sesion", true).orElse(0.0);
        Double fastest = metrics.count(uw, "run_ritmo") > 0 ? metrics.best(uw, "run_ritmo", false).orElse(null) : null;
        Double best5k = null;
        for (String k : List.of("run_5k_tiempo_s", "run_5k_estimado")) {
            if (metrics.count(uw, k) > 0) {
                double v = metrics.best(uw, k, false).orElse(Double.MAX_VALUE);
                best5k = best5k == null ? v : Math.min(best5k, v);
            }
        }
        double total = metrics.sumLastDays(uw, "run_km_sesion", 36500);
        w.add(Widget.statRow("records", "Tus récords", List.of(
                new Stat("Más larga", longest > 0 ? km(longest) : "—", "km", null, null),
                new Stat("Ritmo más rápido", fastest == null ? "—" : pace(fastest.intValue()), "/km", null, null),
                new Stat("Mejor 5K", best5k == null ? "—" : RunningMath.time(best5k), null, "real o estimado", null),
                new Stat("Total", km(total), "km", null, total >= 100 ? "good" : null))));
        if (total == 0) {
            w.add(Widget.notice("sin_datos", "info", "Aún no hay kilómetros",
                    "Registra la distancia y el tiempo de tus salidas y aquí verás crecer tu volumen y bajar tu ritmo."));
        }
        return w;
    }

    @Override
    public SessionUi sessionUi() {
        return new SessionUi("interval", List.of(), List.of("distance", "duration", "rpe"), List.of("duration", "distance"),
                List.of("Prueba del habla: en los rodajes suaves debes poder decir frases completas.",
                        "Pasos cortos y rápidos (cadencia ágil) cargan menos rodillas y tobillos que zancadas largas.",
                        "Bebe agua antes de salir y, si pasas de 60 minutos, a sorbos cada 20–30 minutos.",
                        "Dolor que cambia tu forma de correr = para y camina. El cansancio es normal, el dolor no."));
    }

    @Override
    public String assistantPersona(WorldContext ctx) {
        Setup s = setup(ctx);
        LocalDate start = s.planStart();
        int pw = Math.min(programWeek(start, LocalDate.now(MetricRepository.ZONE)) + 1, s.totalWeeks());
        String paces = s.paces() == null ? "sin ritmos calculados (usa la prueba del habla y el RPE)"
                : "5K de referencia " + RunningMath.time(s.paces().fiveKS()) + "; suave " + s.paces().easyRange() + ", tempo "
                        + pace(s.paces().tempo()) + " /km, intervalos " + pace(s.paces().interval()) + " /km";
        String mode = switch (s.mode()) {
            case WALK -> "plan de caminata sin impacto de carrera";
            case WALK_RUN -> "progresión caminar-correr";
            case RUN -> "plan periodizado de carrera";
        };
        return """
                Eres un entrenador de running. Hablas de rodajes suaves, intervalos, tempo, tirada larga, ritmos por km,
                cadencia, la prueba del habla, la regla del 10 %% (no subir el volumen semanal más de un 10 %%), las semanas
                de descarga y la distribución 80/20 (80 %% suave). El usuario sigue la ruta "%s" (%s), semana %d de %d.
                Ritmos: %s.%s Nunca subas el volumen ni la intensidad por encima del plan; ante dolor que altera la zancada,
                recomienda parar, caminar y consultar. Ritmos y predicciones solo a partir de sus datos (Riegel, Cooper).
                """.formatted(s.track().name(), mode, pw, s.totalWeeks(), paces,
                s.restriction() == null ? "" : " Restricción: " + s.restriction() + ".");
    }
}
