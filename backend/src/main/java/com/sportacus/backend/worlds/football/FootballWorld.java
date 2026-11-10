package com.sportacus.backend.worlds.football;

import static com.sportacus.backend.world.form.Forms.multi;
import static com.sportacus.backend.world.form.Forms.opt;
import static com.sportacus.backend.world.form.Forms.optional;
import static com.sportacus.backend.world.form.Forms.range;
import static com.sportacus.backend.world.form.Forms.showIf;
import static com.sportacus.backend.world.form.Forms.single;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.catalog.CatalogModels.WorldMovement;
import com.sportacus.backend.world.catalog.SafetyFilter;
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
import com.sportacus.backend.world.core.PlanView.SessionView;
import com.sportacus.backend.world.core.Prescription;
import com.sportacus.backend.world.core.SessionOutcome;
import com.sportacus.backend.world.core.SessionUi;
import com.sportacus.backend.world.core.UserContext;
import com.sportacus.backend.world.core.Widget;
import com.sportacus.backend.world.core.Widget.Bar;
import com.sportacus.backend.world.core.Widget.Insight;
import com.sportacus.backend.world.core.Widget.Point;
import com.sportacus.backend.world.core.Widget.Stat;
import com.sportacus.backend.world.core.Widget.Zone;
import com.sportacus.backend.world.core.WorldContext;
import com.sportacus.backend.world.core.WorldModule;
import com.sportacus.backend.world.core.WorldToolkit;
import com.sportacus.backend.world.form.FormSpec;
import com.sportacus.backend.world.form.Forms;
import com.sportacus.backend.world.metrics.MetricRepository;
import com.sportacus.backend.world.session.SessionLogRepository;
import com.sportacus.backend.worlds.football.LoadMath.Acwr;
import com.sportacus.backend.worlds.football.LoadMath.DayLoad;
import com.sportacus.backend.worlds.football.LoadMath.WeekLoad;
import com.sportacus.backend.worlds.football.Microcycle.Slot;
import com.sportacus.backend.worlds.football.Microcycle.Type;

/**
 * Mundo Fútbol: preparación física y técnica alrededor del día de partido.
 * <ul>
 * <li>Microciclo semanal relativo al partido (MD+1 … MD-1) más el propio partido como sesión ({@link Microcycle}).</li>
 * <li>Cada sesión abre con un calentamiento preventivo en 3 partes, basado en el programa FIFA 11+ (Bizzini y
 * Dvorak, 2015), descrito con texto propio.</li>
 * <li>Carga interna sRPE (Foster et al., 2001) y ACWR (Gabbett, 2016) calculados sobre la fecha real de cada
 * sesión ({@link LoadMath}, {@link FootballLoadRepository}).</li>
 * <li>Ajustes por posición (portero, defensa, lateral, mediocampista, extremo, delantero) y por ruta.</li>
 * </ul>
 */
@Component
public class FootballWorld implements WorldModule {

    public static final String KEY = "football";
    static final List<String> PREVENTION_KEYS = List.of("fut_nordico", "fut_nordico_asistido", "fut_copenhague",
            "fut_copenhague_corto", "fut_equilibrio_unipodal", "fut_equilibrio_simple");
    private static final String WARMUP_NAME = "Calentamiento preventivo (basado en FIFA 11+)";

    private final WorldToolkit kit;
    private final MetricRepository metrics;
    private final SessionLogRepository sessionLogs;
    private final FootballLoadRepository loads;

    public FootballWorld(WorldToolkit kit, MetricRepository metrics, SessionLogRepository sessionLogs,
            FootballLoadRepository loads) {
        this.kit = kit;
        this.metrics = metrics;
        this.sessionLogs = sessionLogs;
        this.loads = loads;
    }

    @Override
    public String key() {
        return KEY;
    }

    // ───────────────────────── Onboarding ─────────────────────────

    @Override
    public FormSpec onboardingForm(UserContext user) {
        List<String> zones = user.health().injuredZones();
        String injuryHint = zones.isEmpty() ? "Nos ayuda a reforzar las zonas que más se lesionan en el fútbol."
                : "En tu chequeo de salud marcaste molestias en: " + String.join(", ", zones) + ".";
        int defaultExtra = Math.max(1, Math.min(4, user.profile().daysOr(3) - 1));
        // Obligatorias solo nivel, contexto, partidos y objetivo; el resto tiene valores por defecto explicados.
        FormSpec form = Forms.form("football", "Tu fútbol", "Así organizamos tu semana alrededor del partido.",
                single("f_level", "¿Cuál es tu nivel de fútbol?", null,
                        opt("1", "🌱", "Empiezo o vuelvo tras mucho tiempo"),
                        opt("2", "🌿", "Juego de vez en cuando"),
                        opt("3", "🌳", "Juego todas las semanas"),
                        opt("4", "🏅", "Juego en una liga o torneo organizado"),
                        opt("5", "🏆", "Competitivo o federado")),
                single("f_context", "¿Dónde juegas normalmente?", null,
                        opt("amigos", "😄", "Con amigos, por diversión"),
                        opt("barrio", "🏘️", "Torneos de barrio o de empresa"),
                        opt("liga", "📋", "Liga con equipo y calendario"),
                        opt("competitivo", "🚀", "Competitivo: categorías, selección o semiprofesional")),
                single("f_matches", "¿Cuántos partidos juegas por semana?", null,
                        opt("0", "➖", "Ninguno ahora mismo"), opt("1", "⚽", "Uno"), opt("2", "⚽", "Dos")),
                multi("f_goals", "¿Qué quieres mejorar?", "Elige hasta 3; la primera es la prioridad.", 3,
                        opt("resistencia", "🫁", "Resistencia"), opt("velocidad", "⚡", "Velocidad"),
                        opt("tecnica", "🎯", "Técnica con balón"), opt("fuerza", "💪", "Fuerza"),
                        opt("prevencion", "🛡️", "Prevenir lesiones")),
                optional(showIf(single("f_match_day", "¿Qué día es tu partido principal?",
                        "Organizamos la semana a su alrededor. Si lo omites, repartimos la semana de forma genérica.",
                        opt("1", null, "Lunes"), opt("2", null, "Martes"), opt("3", null, "Miércoles"),
                        opt("4", null, "Jueves"), opt("5", null, "Viernes"), opt("6", null, "Sábado"),
                        opt("7", null, "Domingo"), opt("ninguno", "🔀", "No tengo partido fijo")),
                        "f_matches", "1", "2")),
                optional(single("f_position", "¿En qué posición juegas?",
                        "Ajustamos los ejercicios a tu puesto. Si lo omites, trabajo general de jugador de campo.",
                        opt("portero", "🧤", "Portero"), opt("defensa", "🛡️", "Defensa central"),
                        opt("lateral", "↔️", "Lateral"), opt("mediocampista", "🔄", "Mediocampista"),
                        opt("extremo", "⚡", "Extremo"), opt("delantero", "🎯", "Delantero"))),
                optional(single("f_injury", "¿Has tenido alguna lesión jugando en el último año?", injuryHint,
                        opt("no", "✅", "No"), opt("isquios", "🦵", "Parte de atrás del muslo (isquiotibiales)"),
                        opt("aductor", "🦵", "Ingle o aductores"), opt("tobillo", "🦶", "Tobillo"),
                        opt("rodilla", "🦵", "Rodilla"), opt("otra", "🩹", "Otra"))),
                optional(single("f_team_trainings", "¿Cuántas veces entrenas con tu equipo por semana?", null,
                        opt("0", "➖", "Ninguna"), opt("1", null, "Una"), opt("2", null, "Dos"),
                        opt("3", null, "Tres"), opt("4", null, "Cuatro o más"))),
                optional(range("f_extra", "¿Cuántas sesiones por tu cuenta puedes hacer a la semana?",
                        "Además de partidos y entrenamientos de equipo.", 1, 4, 1, "sesiones")),
                optional(single("f_surface", "¿Dónde sueles jugar o entrenar?", null,
                        opt("sintetica", "🟩", "Cancha sintética"), opt("natural", "🌱", "Césped natural"),
                        opt("calle", "🏙️", "Calle, parque o cancha de cemento"))),
                optional(multi("f_equipment", "¿Qué material tienes para entrenar por tu cuenta?",
                        "Lo precargamos con el equipo de tu perfil.",
                        opt("balon", "⚽", "Balón"), opt("conos", "🔺", "Conos o marcas"),
                        opt("ninguno", "➖", "Nada de esto"))));
        return form.withAnswers(FootballAdaptations.prefill(user.profile(), defaultExtra));
    }

    @Override
    public List<Factor> discoveryFactors(DiscoveryInput in) {
        List<Factor> f = new ArrayList<>();
        Answers a = in.answers();
        UserContext u = in.user();
        if (a.contains("d_motivation", "equipo")) f.add(Factor.of("motivacion", 22, "Quieres jugar, competir y compartir: el fútbol es exactamente eso"));
        if (a.contains("d_enjoy", "balon")) f.add(Factor.of("gusto", 18, "Te gustan los deportes con balón"));
        if ("equipo".equals(a.string("d_social"))) f.add(Factor.of("social", 10, "Prefieres entrenar en equipo"));
        else if ("acompanado".equals(a.string("d_social"))) f.add(Factor.of("social", 3, "Te gusta entrenar acompañado"));
        else if ("solo".equals(a.string("d_social"))) f.add(Factor.of("social", -4, "Prefieres entrenar solo (aunque las sesiones propias son individuales)"));
        if ("juego".equals(a.string("d_motivator"))) f.add(Factor.of("motivador", 10, "Te motiva el juego y la competencia"));
        if (a.contains("d_experience", "futbol")) f.add(Factor.of("experiencia", 10, "Ya has jugado al fútbol"));
        if (u.profile().hasEnvironment("cancha")) f.add(Factor.of("entorno", 6, "Tienes acceso a una cancha"));
        if ("suave".equals(a.string("d_intensity"))) f.add(Factor.of("intensidad", -8, "El fútbol tiene sprints y choques; buscas algo más suave"));
        if (u.health().mustRefer()) f.add(Factor.of("seguridad", -25, "Antes de un deporte intermitente e intenso conviene la valoración de un profesional"));
        if (u.health().hasLowerLimbIssue()) f.add(Factor.of("lesion", -15, "Con molestias en rodilla, cadera, tobillo o pie, los giros y saltos del fútbol exigen cautela"));
        if (u.profile().ageOr(30) >= 60) f.add(Factor.of("edad", -6, "Con más de 60 años es posible, mejor en modo recreativo y con calentamiento preventivo"));
        return f;
    }

    @Override
    public EnrollmentResult enroll(UserContext user, Answers a) {
        int level = clamp(a.integer("f_level", 2), 1, 5);
        String context = a.string("f_context", "amigos");
        List<String> goals = a.list("f_goals");
        String injury = a.string("f_injury", "no"); // sin respuesta: lo que diga el chequeo de salud
        String position = a.string("f_position", FootballAdaptations.GENERIC_POSITION);
        int matches = clamp(a.integer("f_matches", 1), 0, 2);
        Integer matchDay = matches == 0 || "ninguno".equals(a.string("f_match_day")) ? null : a.integer("f_match_day");
        if (matchDay != null && (matchDay < 1 || matchDay > 7)) matchDay = null;
        int team = clamp(a.integer("f_team_trainings", 0), 0, 4);
        int own = clamp(a.integer("f_extra", Math.max(1, user.profile().daysOr(3) - 1)), 1, 4);
        HealthSummary h = user.health();
        List<Factor> why = new ArrayList<>();

        String track;
        if (!"no".equals(injury) || h.hasLowerLimbIssue()) {
            track = "prevencion";
            why.add(Factor.of("ruta", 0, "Ruta Prevención de lesiones: " + (!"no".equals(injury)
                    ? "tuviste una lesión jugando en el último año (la lesión previa es el mayor factor de riesgo)"
                    : "tu chequeo señala molestias en el tren inferior")));
        } else if ("liga".equals(context) || "competitivo".equals(context)) {
            track = "rendimiento";
            why.add(Factor.of("ruta", 0, "Ruta Rendimiento: juegas en liga o a nivel competitivo"));
        } else if (!goals.isEmpty() && "prevencion".equals(goals.get(0))) {
            track = "prevencion";
            why.add(Factor.of("ruta", 0, "Ruta Prevención de lesiones: es tu objetivo principal"));
        } else {
            track = "recreativo";
            why.add(Factor.of("ruta", 0, "Ruta Recreativa: juegas por diversión; mejoramos tu condición sin sobrecargarte"));
        }
        List<String> notices = new ArrayList<>(FootballAdaptations.intensityNotices(h, kit.safety().highImpactRestricted(user)));
        if (!h.vigorousAllowed() && level > 2) level = 2;
        if (user.profile().ageOr(30) >= 60 && level > 3) level = 3;
        why.add(Factor.of("nivel", 0, "Nivel " + level + " de 5 según tu experiencia"));
        if (a.has("f_position")) why.add(Factor.of("posicion", 0, "Posición: " + positionName(position)));
        why.addAll(FootballAdaptations.missingDefaults(a, user.profile()));
        notices.addAll(FootballAdaptations.profileNotices(user.insights()));

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("matchDay", matchDay);
        state.put("matches", matches);
        state.put("team", team);
        state.put("own", own);
        state.put("position", position);
        state.put("surface", a.string("f_surface")); // sin respuesta: la portería solo si tu perfil incluye cancha
        state.put("equipment", FootballAdaptations.equipment(a, user.profile()));
        state.put("injury", a.string("f_injury")); // null = no lo contó: prevención primero
        state.put("goals", goals);

        List<GoalSpec> g = new ArrayList<>();
        switch (track) {
            case "prevencion" -> g.add(new GoalSpec("fut_prevencion_4s", "Hacer nórdicos y Copenhague 4 semanas seguidas",
                    Map.of("weeks", 4, "movements", PREVENTION_KEYS), LocalDate.now().plusWeeks(5)));
            case "rendimiento" -> g.add(new GoalSpec("fut_sprint", "Bajar un 3 % tu tiempo de sprint de 30 m",
                    Map.of("metric", "fut_sprint_30m_s", "improvePct", 3), LocalDate.now().plusWeeks(8)));
            default -> g.add(new GoalSpec("fut_carga_optima", "Mantener tu carga en zona óptima (ACWR 0,8–1,3) 4 semanas",
                    Map.of("metric", "fut_acwr", "min", 0.8, "max", 1.3, "weeks", 4), LocalDate.now().plusWeeks(7)));
        }
        if (matchDay == null && matches > 0) {
            notices.add("Sin partido fijo repartimos la semana de forma genérica; registra tus partidos desde el plan para contar su carga.");
        }
        if (team + own + matches >= 7) {
            notices.add("Tu semana es muy cargada: vigila tu indicador ACWR y respeta la recuperación.");
        }
        if (h.mustRefer()) {
            notices.add("Tu chequeo indica que consultes a un profesional antes de jugar o entrenar fuerte.");
        }
        return new EnrollmentResult(track, level, a.asMap(), state, g, Map.of(), why, notices);
    }

    // ───────────────────────── Plan ─────────────────────────

    /** Parámetros del usuario que afectan a la composición de las sesiones. */
    /**
     * @param injuryUnknown no contó si tuvo lesiones: prevención primero
     * @param rpeOnly       betabloqueadores: intensidad por RPE, nunca por % de frecuencia cardiaca
     * @param avoidContact  anticoagulantes: sin duelos, saltos ni caídas
     */
    record Params(int level, String track, String position, boolean restricted, boolean vigorous, int minutes,
            Set<String> goals, boolean ankleOrHamstring, boolean injuryUnknown, boolean rpeOnly, boolean avoidContact) {
        boolean attacker() {
            return "delantero".equals(position) || "extremo".equals(position);
        }

        boolean keeper() {
            return "portero".equals(position);
        }

        boolean prevention() {
            return "prevencion".equals(track) || goals.contains("prevencion") || ankleOrHamstring || injuryUnknown;
        }
    }

    @Override
    public GeneratedPlan generatePlan(WorldContext ctx) {
        UserContext user = ctx.user();
        var w = ctx.world();
        String track = w.trackKey() == null ? "recreativo" : w.trackKey();
        Integer matchDay = w.stateNumber("matchDay") == null ? null : w.stateNumber("matchDay").intValue();
        int matches = intState(ctx, "matches", 1);
        int team = intState(ctx, "team", 0);
        int own = intState(ctx, "own", Math.max(1, Math.min(4, user.profile().daysOr(3) - 1)));
        String position = w.stateString("position") == null ? FootballAdaptations.GENERIC_POSITION : w.stateString("position");
        String injury = w.stateString("injury");
        Set<String> goals = new HashSet<>(w.profile().list("f_goals"));
        boolean restricted = kit.safety().highImpactRestricted(user);
        var insights = user.insights();
        Params p = new Params(w.level(), track, position, restricted, user.health().vigorousAllowed(),
                user.profile().minutesOr(60), goals,
                "tobillo".equals(injury) || "isquios".equals(injury) || "aductor".equals(injury),
                injury == null, insights.heartRateUnreliable(), insights.avoidContact());

        Set<String> equipment = equipmentFor(ctx);
        // Evita el computeIfAbsent anidado de CatalogRepository ("Recursive update"): primero el índice global.
        kit.catalog().movement(PREVENTION_KEYS.get(0));
        List<WorldMovement> usable = kit.usableMovements(KEY, user, equipment);
        if (p.avoidContact()) {
            usable = usable.stream().filter(m -> !FootballAdaptations.contactOrFallRisk(m.pattern())).toList();
        }
        Pool pool = new Pool(usable, p.level(), kit.liked(user.userId(), KEY), kit.disliked(user.userId(), KEY),
                WorldToolkit.stableRandom(user.userId(), KEY + track + position), kit.safety(), user);

        int ownDays = matchDay == null ? own + team : own;
        Microcycle.Result cycle = Microcycle.build(new Microcycle.Input(matchDay, matches, team, own,
                kit.weekdays(user.profile(), Math.min(7, ownDays))));
        List<Slot> slots = new ArrayList<>(cycle.slots());
        slots.sort(Comparator.comparing((Slot s) -> s.weekday() == null ? 99 : s.weekday()));

        int matchMinutes = "liga".equals(w.profile().string("f_context")) || "competitivo".equals(w.profile().string("f_context"))
                ? 90 : 60;
        List<GeneratedSession> sessions = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            pool.resetDay();
            List<GeneratedBlock> blocks = compose(s.type(), p, pool, matchMinutes);
            Integer est = s.type() == Type.EQUIPO ? Integer.valueOf(75)
                    : s.type() == Type.PARTIDO ? Integer.valueOf(matchMinutes + 15) : null;
            sessions.add(new GeneratedSession(0, i, s.weekday(), s.title(), focusOf(s.type(), blocks), s.type().kind,
                    est, notesFor(s), blocks));
        }

        List<Factor> why = new ArrayList<>();
        if (matchDay != null && matches > 0) {
            why.add(Factor.of("microciclo", 0, "Semana organizada alrededor de tu partido del " + WorldToolkit.dayName(matchDay).toLowerCase()
                    + ": fuerza lejos del partido (MD-4), resistencia en MD-3, velocidad en MD-2 y activación corta en MD-1"));
        } else {
            why.add(Factor.of("microciclo", 0, "Sin partido fijo: repartimos fuerza, velocidad y resistencia en tus días disponibles"));
        }
        if (team > 0) {
            why.add(Factor.of("equipo", 0, "Colocamos tus " + team + " entrenamientos de equipo en días típicos; tus sesiones propias los complementan con más prevención y menos acondicionamiento. Si tu equipo entrena otros días, regístralos igual: cuentan para tu carga"));
        }
        if (cycle.droppedOwn() > 0) {
            why.add(Factor.of("dias", 0, cycle.droppedOwn() + " sesión(es) propia(s) no caben sin romper la recuperación alrededor del partido"));
        }
        why.add(Factor.of("calentamiento", 0, "Cada sesión empieza con un calentamiento preventivo en 3 partes basado en el programa FIFA 11+ (Bizzini y Dvorak, 2015)"));
        why.add(Factor.of("carga", 0, "Controlamos tu carga con RPE × minutos (Foster et al., 2001) y la relación aguda:crónica (Gabbett, 2016)"));
        why.add(Factor.of("posicion", 0, positionRationale(position)));
        why.add(Factor.of("ruta", 0, switch (track) {
            case "prevencion" -> "Ruta Prevención: más volumen de nórdicos, Copenhague y equilibrio";
            case "rendimiento" -> "Ruta Rendimiento: añadimos sprints repetidos (RSA) y más velocidad";
            default -> "Ruta Recreativa: volumen moderado para llegar entero al partido";
        }));
        if (restricted) {
            why.add(Factor.of("seguridad", 0, FootballAdaptations.restrictionRationale(user.health())));
        } else if (!p.vigorous()) {
            why.add(Factor.of("seguridad", 0, user.health().moderateReason()));
        }
        why.addAll(FootballAdaptations.planFactors(insights));
        if (!equipment.contains("balon")) {
            why.add(Factor.of("material", 0, "Sin balón no podemos incluir técnica: si consigues uno, regenera el plan"));
        }
        String title = "Fútbol · " + switch (track) {
            case "prevencion" -> "prevención";
            case "rendimiento" -> "rendimiento";
            default -> "recreativo";
        } + " · " + positionName(position).toLowerCase();
        return new GeneratedPlan(title, "Semana tipo alrededor del partido que se repite; la carga se vigila con tu ACWR.",
                1, null, why, sessions);
    }

    /** Bloques de una sesión según su tipo, ajustados a posición, ruta, nivel y minutos. */
    List<GeneratedBlock> compose(Type type, Params p, Pool pool, int matchMinutes) {
        List<GeneratedBlock> warm = new ArrayList<>();
        List<GeneratedBlock> main = new ArrayList<>();
        GeneratedBlock cool = null;
        int budget = p.minutes();
        switch (type) {
            case EQUIPO -> {
                return List.of(new GeneratedBlock("principal", null, "Entrenamiento con tu equipo",
                        Prescription.time(75 * 60, "lo que marque tu entrenador"), null, null,
                        "Regístralo con tu RPE y los minutos: cuenta para tu carga semanal."));
            }
            case PARTIDO -> {
                warm.addAll(warmup(pool, p, false, false));
                main.add(new GeneratedBlock("principal", null, "Partido",
                        Prescription.time(matchMinutes * 60, "competición"), null, null,
                        "Registra los minutos que jugaste y tu esfuerzo (RPE) al terminar."));
                return concat(warm, main, null);
            }
            case FUERZA -> {
                warm.addAll(warmup(pool, p, false, budget < 40));
                add(main, pool, p, "principal", "nordico");
                add(main, pool, p, "principal", "copenhague");
                add(main, pool, p, "principal", "unipodal");
                if (p.prevention()) add(main, pool, p, "principal", "equilibrio");
                add(main, pool, p, "accesorio", "gluteo");
                add(main, pool, p, "accesorio", "core");
                if (p.level() >= 2 && (p.keeper() || "defensa".equals(p.position()) || "rendimiento".equals(p.track()))) {
                    add(main, pool, p, "principal", "pliometria");
                }
                if (p.prevention()) add(main, pool, p, "accesorio", "gemelo");
                cool = block(pool, p, "vuelta_calma", "movilidad");
            }
            case RESISTENCIA -> {
                warm.addAll(warmup(pool, p, false, budget < 40));
                if (p.keeper()) {
                    add(main, pool, p, "principal", "portero_lateral", "cambio_direccion");
                    add(main, pool, p, "principal", "portero_reaccion");
                    add(main, pool, p, "intervalo", "intervalos", "posesion");
                } else if ("mediocampista".equals(p.position())) {
                    add(main, pool, p, "intervalo", "aerobico", "intervalos");
                    add(main, pool, p, "intervalo", "intervalos", "posesion");
                    add(main, pool, p, "intervalo", "posesion");
                } else {
                    if (p.level() == 1) add(main, pool, p, "intervalo", "aerobico", "intervalos", "posesion");
                    else add(main, pool, p, "intervalo", "intervalos", "aerobico", "posesion");
                    if ("rendimiento".equals(p.track()) && p.vigorous()) add(main, pool, p, "intervalo", "rsa");
                    add(main, pool, p, "intervalo", "posesion");
                }
                cool = block(pool, p, "vuelta_calma", "vuelta_calma");
            }
            case VELOCIDAD -> {
                warm.addAll(warmup(pool, p, false, budget < 40));
                if (p.keeper()) {
                    add(main, pool, p, "principal", "portero_reaccion");
                    add(main, pool, p, "principal", "portero_lateral", "cambio_direccion");
                    add(main, pool, p, "tecnica", "portero_blocaje");
                    add(main, pool, p, "principal", "sprint", "reaccion");
                } else {
                    add(main, pool, p, "principal", "sprint", "reaccion");
                    add(main, pool, p, "principal", "cambio_direccion");
                    if (p.attacker() || "rendimiento".equals(p.track())) add(main, pool, p, "principal", "sprint_largo");
                    if ("defensa".equals(p.position())) add(main, pool, p, "principal", "aereo");
                    if (p.attacker()) add(main, pool, p, "tecnica", "tiro");
                    add(main, pool, p, "tecnica", "conduccion");
                    add(main, pool, p, "tecnica", "pase");
                    add(main, pool, p, "tecnica", "control");
                    add(main, pool, p, "tecnica", "dominio");
                }
                cool = block(pool, p, "vuelta_calma", "vuelta_calma");
            }
            case ACTIVACION -> {
                budget = Math.min(budget, 35);
                warm.addAll(warmup(pool, p, false, true));
                add(main, pool, p, "principal", "reaccion");
                add(main, pool, p, "tecnica", p.keeper() ? "portero_blocaje" : "pase");
                add(main, pool, p, "tecnica", p.keeper() ? "portero_reaccion" : "control");
            }
            case RECUPERACION -> {
                budget = Math.min(budget, 40);
                warm.addAll(warmup(pool, p, true, true));
                GeneratedBlock rec = block(pool, p, "principal", "recuperacion");
                if (rec != null) main.add(rec);
                add(main, pool, p, "vuelta_calma", "movilidad");
                add(main, pool, p, "vuelta_calma", "foam");
            }
            case PREVENCION -> {
                warm.addAll(warmup(pool, p, false, budget < 40));
                add(main, pool, p, "principal", "nordico");
                add(main, pool, p, "principal", "copenhague");
                add(main, pool, p, "principal", "equilibrio");
                add(main, pool, p, "accesorio", "core");
                add(main, pool, p, "tecnica", p.keeper() ? "portero_blocaje" : "dominio");
                add(main, pool, p, "tecnica", "pase");
                cool = block(pool, p, "vuelta_calma", "movilidad");
            }
        }
        return fit(warm, main, cool, budget);
    }

    /** Calentamiento preventivo en 3 partes (basado en FIFA 11+); en recuperación, solo las 2 primeras y suaves. */
    private List<GeneratedBlock> warmup(Pool pool, Params p, boolean soft, boolean shortVersion) {
        List<GeneratedBlock> out = new ArrayList<>();
        int runS = shortVersion ? 180 : 240;
        int strengthS = shortVersion ? 180 : "prevencion".equals(p.track()) ? 360 : 300;
        WorldMovement run = pool.pickSoftOrHard("cal_carrera", soft);
        if (run != null) {
            out.add(new GeneratedBlock("calentamiento", run.key(), WARMUP_NAME + " · 1. " + run.name(),
                    Prescription.time(runS, "suave"), null, null,
                    "Calentamiento descrito con palabras propias, basado en el programa FIFA 11+ (Bizzini y Dvorak, 2015)."));
        }
        WorldMovement strength = pool.pickSoftOrHard("cal_fuerza", soft);
        if (strength != null) {
            out.add(GeneratedBlock.of("calentamiento", strength.key(), WARMUP_NAME + " · 2. " + strength.name(),
                    Prescription.time(strengthS, "controlado")));
        }
        if (!soft) {
            WorldMovement speed = pool.pickSoftOrHard("cal_velocidad", false);
            if (speed != null) {
                out.add(GeneratedBlock.of("calentamiento", speed.key(), WARMUP_NAME + " · 3. " + speed.name(),
                        Prescription.interval(3, 10, 40, "progresivo")));
            }
        }
        return out;
    }

    private void add(List<GeneratedBlock> into, Pool pool, Params p, String blockType, String... patterns) {
        GeneratedBlock b = block(pool, p, blockType, patterns);
        if (b != null) into.add(b);
    }

    private GeneratedBlock block(Pool pool, Params p, String blockType, String... patterns) {
        for (String pattern : patterns) {
            WorldMovement m = pool.pick(pattern, p.restricted() && "recuperacion".equals(pattern));
            if (m != null) return GeneratedBlock.of(blockType, m.key(), m.name(), prescriptionFor(m.key(), p));
        }
        return null;
    }

    /** Calentamiento siempre; el resto mientras quepa en los minutos (el primer bloque principal siempre entra). */
    private static List<GeneratedBlock> fit(List<GeneratedBlock> warm, List<GeneratedBlock> main, GeneratedBlock cool,
            int budgetMin) {
        int budget = budgetMin * 60;
        List<GeneratedBlock> out = new ArrayList<>(warm);
        int total = warm.stream().mapToInt(FootballWorld::cost).sum();
        int coolCost = cool == null ? 0 : cost(cool);
        boolean first = true;
        for (GeneratedBlock b : main) {
            if (first || total + cost(b) + coolCost <= budget) {
                out.add(b);
                total += cost(b);
            }
            first = false;
        }
        if (cool != null && total + coolCost <= budget + 120) out.add(cool);
        return out;
    }

    private static List<GeneratedBlock> concat(List<GeneratedBlock> a, List<GeneratedBlock> b, GeneratedBlock c) {
        List<GeneratedBlock> out = new ArrayList<>(a);
        out.addAll(b);
        if (c != null) out.add(c);
        return out;
    }

    private static int cost(GeneratedBlock b) {
        return b.prescription().estimatedSeconds() + 30;
    }

    /** Prescripción por ejercicio según nivel, ruta y posición. */
    static Prescription prescriptionFor(String key, Params p) {
        int l = p.level();
        int prev = "prevencion".equals(p.track()) ? 1 : 0;
        String hard = p.vigorous() ? "fuerte" : "moderada (RPE 6)";
        return switch (key) {
            case "fut_nordico", "fut_nordico_asistido" -> Prescription.reps(2 + (l >= 3 ? 1 : 0) + prev,
                    l <= 2 ? 4 : l <= 4 ? 6 : 8, 90).withCue("Bajada lenta de 3–5 s");
            case "fut_copenhague", "fut_copenhague_corto" -> Prescription.hold(2 + prev, Math.min(40, 15 + 5 * l), 60)
                    .withSide("cada_lado");
            case "fut_sentadilla_unipodal", "fut_sentadilla_split" -> Prescription
                    .reps(3 + ("defensa".equals(p.position()) ? 1 : 0), 6, 10, 75).withSide("cada_lado");
            case "puente_gluteo" -> Prescription.reps(3, 12, 15, 60);
            case "fut_puente_unipodal" -> Prescription.reps(3, 8, 12, 60).withSide("cada_lado");
            case "plancha" -> Prescription.hold(3, 30 + 5 * Math.min(l, 4), 45);
            case "fut_plancha_lateral" -> Prescription.hold(2, 20 + 5 * Math.min(l, 4), 30).withSide("cada_lado");
            case "fut_equilibrio_unipodal", "fut_equilibrio_simple" -> Prescription.hold(2 + prev, 30, 20).withSide("cada_lado");
            case "fut_saltos_aterrizaje" -> Prescription.reps(3, 5, 60).withCue("Congela 2 s cada caída");
            case "fut_elevacion_talones_unipodal" -> Prescription.reps(3, 12, 45).withSide("cada_lado");
            case "fut_intervalos_15_15" -> Prescription.interval(
                    Math.min(24, 8 + 3 * l) + ("mediocampista".equals(p.position()) ? 4 : 0), 15, 15, hard)
                    .withCue("Divide en 2 bloques con 3 minutos suaves entre ellos");
            case "fut_intervalos_4x4" -> Prescription.interval(l >= 3 ? 4 : 3, 240, 180,
                    !p.vigorous() ? hard : p.rpeOnly() ? "fuerte: RPE 8, solo frases cortas" : "fuerte: frases cortas (≈ 90 % FC máx.)");
            case "fut_carrera_continua" -> Prescription.time((15 + 5 * Math.min(l, 3)
                    + ("mediocampista".equals(p.position()) ? 5 : 0)) * 60, "suave, conversacional");
            case "fut_intervalos_bici" -> Prescription.interval(Math.min(16, 6 + 2 * l), 30, 30, hard);
            case "fut_rsa_sprints_repetidos" -> Prescription.interval(6 + (l >= 4 ? 2 : 0), 4, 20, "máxima")
                    .withCue("Una serie (dos si te sientes fresco) con 4 minutos suaves entre series");
            case "fut_posesion_simulada", "fut_pases_continuos" -> p.avoidContact()
                    ? Prescription.interval(4, 180, 60, "cambios de ritmo").withCue("Solo o con pases: sin disputar el balón")
                    : Prescription.interval(4, 180, 60, "cambios de ritmo");
            case "fut_sprint_10m" -> Prescription.interval(5 + (l >= 3 ? 1 : 0) + (p.attacker() ? 2 : 0)
                    + ("rendimiento".equals(p.track()) ? 1 : 0), 2, 60, "máxima")
                    .withCue("Recuperación completa: vuelve caminando");
            case "fut_sprint_20_30m" -> Prescription.interval(3 + (p.attacker() ? 2 : 0), 5, 120, "máxima")
                    .withCue("Recupera 2 minutos completos entre sprints");
            case "fut_salidas_posiciones" -> Prescription.interval(6, 3, 60, "máxima");
            case "fut_aceleraciones_bici" -> Prescription.interval(6, 8, 75, "máxima");
            case "fut_cambio_direccion_5105" -> Prescription.interval(4 + (l >= 3 ? 2 : 0), 6, 90, "máxima");
            case "fut_desplazamientos_controlados" -> Prescription.interval(5, 20, 40, "media");
            case "fut_reaccion_senal" -> Prescription.interval(6, 3, 45, "reacción");
            case "fut_conduccion_zigzag" -> Prescription.interval(5, 30, 30, "técnica").withCue("Alterna pie derecho e izquierdo");
            case "fut_pases_pared" -> Prescription.reps(3, 20, 30).withCue("Alterna pies y distancias");
            case "fut_control_orientado" -> Prescription.reps(3, 10, 30).withSide("ambos");
            case "fut_dominadas_balon" -> Prescription.reps(3, 10, Math.max(20, 25 * l), 30)
                    .withCue("Registra en repeticiones tu mejor racha de toques");
            case "fut_tiro_porteria" -> Prescription.reps(3, 5, 60).withCue("Precisión antes que potencia");
            case "fut_cabeceo_saltos" -> Prescription.reps(3, 5, 60);
            case "fut_portero_reaccion" -> Prescription.interval(8, 20, 40, "reacción");
            case "fut_portero_lateral" -> Prescription.interval(6 + (l >= 3 ? 2 : 0), 8, 45, "explosiva");
            case "fut_portero_blocaje" -> Prescription.reps(3, 10, 30);
            case "fut_trote_regenerativo", "fut_bici_suave", "fut_caminata_activa" ->
                    Prescription.time(l >= 3 ? 1200 : 900, "muy suave");
            case "fut_movilidad_futbolista" -> Prescription.time(360, "suave");
            case "fut_foam_roller", "fut_vuelta_calma" -> Prescription.time(300, "suave");
            default -> Prescription.time(300, "moderada");
        };
    }

    private static String focusOf(Type type, List<GeneratedBlock> blocks) {
        if (type == Type.EQUIPO || type == Type.PARTIDO) return type.label;
        return String.join(" · ", blocks.stream().filter(b -> !"calentamiento".equals(b.blockType()))
                .map(GeneratedBlock::title).limit(3).toList());
    }

    private static String notesFor(Slot s) {
        return switch (s.type()) {
            case PARTIDO -> "Calienta con el protocolo preventivo antes de jugar. Registra minutos y RPE.";
            case EQUIPO -> "Sigue a tu entrenador; registra la sesión para contar su carga.";
            case FUERZA -> "El día más exigente en fuerza, lejos del partido.";
            case RESISTENCIA -> "Esfuerzos intermitentes como en el partido.";
            case VELOCIDAD -> "Sprints con recuperación completa: calidad, no cansancio.";
            case ACTIVACION -> "Corta y viva: termina con ganas de más para llegar fresco al partido.";
            case RECUPERACION -> "Si jugaste más de 60 minutos, muy suave; si no jugaste, puedes hacer una sesión de prevención.";
            case PREVENCION -> "Fuerza preventiva y técnica sin fatiga.";
        };
    }

    private Set<String> equipmentFor(WorldContext ctx) {
        Set<String> eq = SafetyFilter.availableEquipment(ctx.user().profile());
        Object declared = ctx.world().stateValue("equipment");
        if (declared instanceof List<?> l) l.forEach(x -> eq.add(String.valueOf(x)));
        String surface = ctx.world().stateString("surface");
        if ("sintetica".equals(surface) || "natural".equals(surface)) eq.add("porteria");
        return eq;
    }

    /** Selector de movimientos de un día (sin repetir) con preferencias estables. */
    static final class Pool {
        private final List<WorldMovement> usable;
        private final int level;
        private final Set<String> liked;
        private final Set<String> disliked;
        private final Random rnd;
        private final SafetyFilter safety;
        private final UserContext user;
        private final Set<String> usedToday = new HashSet<>();

        Pool(List<WorldMovement> usable, int level, Set<String> liked, Set<String> disliked, Random rnd,
                SafetyFilter safety, UserContext user) {
            this.usable = usable;
            this.level = level;
            this.liked = liked;
            this.disliked = disliked;
            this.rnd = rnd;
            this.safety = safety;
            this.user = user;
        }

        void resetDay() {
            usedToday.clear();
        }

        /** Mejor movimiento del patrón; con {@code soft} prefiere el de menor impacto. */
        WorldMovement pick(String pattern, boolean soft) {
            List<WorldMovement> all = usable.stream().filter(m -> pattern.equals(m.pattern())).toList();
            List<WorldMovement> inLevel = all.stream().filter(m -> m.level() <= level + 1).toList();
            WorldMovement m = WorldToolkit.pick(inLevel.isEmpty() ? all : inLevel, usedToday, wm -> {
                double s = 10 - Math.abs(wm.level() - level) * 2;
                int impact = impactRank(wm.movement().impact());
                s += soft ? -impact * 2 : impact * 1.5; // sin restricciones, el ejercicio más específico
                if (!safety.cautions(wm.movement(), user).isEmpty()) s -= 4; // alternativa sin precaución si existe
                if (liked.contains(wm.key())) s += 4;
                if (disliked.contains(wm.key())) s -= 8;
                return s;
            }, rnd);
            if (m != null) usedToday.add(m.key());
            return m;
        }

        WorldMovement pickSoftOrHard(String pattern, boolean soft) {
            return pick(pattern, soft);
        }

        private static int impactRank(String impact) {
            return "alto".equals(impact) ? 2 : "medio".equals(impact) ? 1 : 0;
        }
    }

    // ───────────────────────── Evaluación ─────────────────────────

    @Override
    public SessionOutcome evaluateSession(WorldContext ctx, LoggedSession session) {
        SessionOutcome.Builder out = SessionOutcome.builder();
        UUID uw = ctx.world().id();

        if ("partido".equals(session.sessionKind())) {
            int minutes = session.durationMin() != null ? session.durationMin()
                    : session.blocks().stream().filter(b -> b.durationS() != null && b.movementKey() == null)
                            .mapToInt(b -> b.durationS() / 60).sum();
            if (minutes > 0) out.metric("fut_minutos_partido", minutes);
            out.achievement("fut_primer_partido", "Primer partido registrado", "⚽");
            out.highlight("Partido registrado" + (minutes > 0 ? ": " + minutes + " min" : "")
                    + ". Mañana toca recuperación activa.");
        }

        // Récord de dominadas de balón registrado en la sesión.
        int bestTouches = session.blocks().stream()
                .filter(b -> b.completed() && "fut_dominadas_balon".equals(b.movementKey()) && b.reps() != null)
                .mapToInt(LoggedBlock::reps).max().orElse(0);
        if (bestTouches > 0) {
            double previous = Math.max(metrics.best(uw, "fut_dominio_record", true).orElse(0.0),
                    metrics.best(uw, "fut_dominio_balon", true).orElse(0.0));
            out.metric("fut_dominio_record", bestTouches);
            if (previous > 0 && bestTouches > previous) {
                out.highlight("¡Récord de dominadas! " + bestTouches + " toques seguidos");
                out.achievement("fut_record_dominadas", "Récord de dominadas de balón", "🎯");
            }
        }

        // Prevención: bloques completados y constancia de 4 semanas.
        Set<String> prevBlocks = new LinkedHashSet<>();
        for (LoggedBlock b : session.blocks()) {
            if (b.completed() && b.movementKey() != null && PREVENTION_KEYS.contains(b.movementKey())) {
                prevBlocks.add(b.planBlockId() != null ? b.planBlockId().toString() : b.movementKey());
            }
        }
        if (!prevBlocks.isEmpty()) {
            out.metric("fut_prevencion", prevBlocks.size());
            LocalDate monday = LocalDate.now(MetricRepository.ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (loads.weeksWithMovements(uw, monday.minusWeeks(3), PREVENTION_KEYS) >= 4) {
                out.achievement("fut_prevencion_4_semanas", "4 semanas seguidas de prevención", "🛡️");
            }
        }

        // ACWR sobre la fecha real de las sesiones (incluida esta).
        Optional<Acwr> acwr = currentAcwr(uw);
        if (acwr.isPresent()) {
            Acwr a = acwr.get();
            out.metric("fut_acwr", a.ratio(), Map.of("acute", Math.round(a.acute()), "chronic", a.chronicWeekly()));
            out.highlight(LoadMath.zoneMessage(a.ratio()));
        } else if (session.rpe() != null) {
            LocalDate first = loads.firstSessionDay(uw);
            long days = first == null ? 0 : java.time.temporal.ChronoUnit.DAYS.between(first, LocalDate.now(MetricRepository.ZONE)) + 1;
            out.highlight("Carga registrada" + (session.sessionLoad() != null ? " (" + Math.round(session.sessionLoad()) + " UA)" : "")
                    + ". Con 3 semanas de registros calcularemos tu relación aguda:crónica (llevas " + Math.max(1, days) + (days <= 1 ? " día)." : " días)."));
        }

        // Subida de nivel: constancia sin picos de carga.
        int total = sessionLogs.countWorld(uw);
        int level = ctx.world().level();
        boolean safeLoad = acwr.map(a -> a.ratio() <= 1.3).orElse(true);
        if (level < 5 && total >= level * 12 && safeLoad) {
            out.level(level + 1);
            out.highlight("Subes a nivel " + (level + 1) + ": más volumen en sprints e intervalos");
            out.regeneratePlan();
        }
        return out.build();
    }

    private Optional<Acwr> currentAcwr(UUID uw) {
        LocalDate today = LocalDate.now(MetricRepository.ZONE);
        return LoadMath.acwr(loads.dailyLoads(uw, today.minusDays(LoadMath.CHRONIC_DAYS - 1L)), today,
                loads.firstSessionDay(uw));
    }

    @Override
    public AssessmentOutcome onAssessment(WorldContext ctx, String key, double value) {
        if (value <= 0) return AssessmentOutcome.none();
        List<String> hl = new ArrayList<>();
        switch (key) {
            case "fut_test_cooper" -> {
                double vo2 = Math.round((value - 504.9) / 44.73 * 10) / 10.0;
                if (vo2 > 0) hl.add("VO₂máx estimado ≈ " + fmt(vo2) + " ml/kg/min (fórmula de Cooper, 1968)");
            }
            case "fut_test_sprint_30" -> hl.add("Repite el test cada 4 semanas en la misma superficie para comparar");
            case "fut_test_dominio" -> hl.add("Lo usaremos como referencia para tus récords de dominadas en las sesiones");
            default -> {
            }
        }
        return new AssessmentOutcome(null, hl, null);
    }

    // ───────────────────────── Dashboard ─────────────────────────

    @Override
    public List<Widget> dashboard(WorldContext ctx, PlanView plan) {
        UUID uw = ctx.world().id();
        LocalDate today = LocalDate.now(MetricRepository.ZONE);
        int dow = today.getDayOfWeek().getValue();
        List<Integer> matchDays = matchDays(ctx);
        Integer toMatch = matchDays.isEmpty() ? null : Microcycle.daysToNext(dow, matchDays);
        List<Widget> w = new ArrayList<>();

        SessionView next = upcoming(plan, dow);
        String subtitle = toMatch == null ? null : toMatch == 0 ? "Hoy hay partido" : toMatch == 1 ? "Partido mañana"
                : "Partido en " + toMatch + " días";
        Map<String, Object> nextData = new LinkedHashMap<>();
        if (next != null) nextData.put("session", next);
        w.add(new Widget("next_session", "next_session", "Tu próxima sesión", subtitle, nextData));

        Optional<Acwr> acwr = currentAcwr(uw);
        if (acwr.isPresent()) {
            Acwr a = acwr.get();
            w.add(Widget.gauge("acwr", "Relación aguda:crónica", "Carga de 7 días ÷ media semanal de 28 días",
                    Math.min(2.5, a.ratio()), 0, 2.5, "", List.of(
                            new Zone(0, 0.8, "info", "Baja"), new Zone(0.8, 1.3, "good", "Óptima"),
                            new Zone(1.3, 1.5, "warn", "Precaución"), new Zone(1.5, 2.5, "bad", "Riesgo")),
                    LoadMath.zoneMessage(a.ratio())));
        } else {
            LocalDate first = loads.firstSessionDay(uw);
            long days = first == null ? 0 : java.time.temporal.ChronoUnit.DAYS.between(first, today) + 1;
            w.add(Widget.notice("acwr_pendiente", "info", "Relación aguda:crónica",
                    "La calculamos con 3 semanas de registros" + (days > 0 ? " (llevas " + days + (days == 1 ? " día" : " días") + ")" : "")
                            + ". Registra RPE y minutos de cada sesión y partido."));
        }

        w.add(weeklyLoadWidget(uw, today, acwr));

        int[] week = loads.sessionsAndMinutesSince(uw, today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        long planned = plan.currentWeekSessions().size();
        w.add(Widget.statRow("semana", "Esta semana", List.of(
                new Stat("Sesiones", plan.doneThisWeek() + "/" + planned, null, week[0] + " registradas desde el lunes",
                        plan.doneThisWeek() >= planned ? "good" : null),
                new Stat("Minutos", String.valueOf(week[1]), "min", null, null),
                new Stat("Próximo partido", toMatch == null ? "—" : toMatch == 0 ? "Hoy" : String.valueOf(toMatch),
                        toMatch == null || toMatch == 0 ? null : toMatch == 1 ? "día" : "días",
                        toMatch == null ? "sin día fijo" : WorldToolkit.dayName(nextMatchDay(dow, matchDays)), null))));

        w.add(Widget.insights("Para ti", insights(ctx, acwr, toMatch, matchDays, dow)));
        return w;
    }

    private SessionView upcoming(PlanView plan, int dow) {
        return plan.currentWeekSessions().stream().filter(s -> !s.done())
                .filter(s -> s.weekday() != null && s.weekday() >= dow)
                .min(Comparator.comparing(SessionView::weekday))
                .orElse(plan.nextSession());
    }

    private Widget weeklyLoadWidget(UUID uw, LocalDate today, Optional<Acwr> acwr) {
        List<DayLoad> daily = loads.dailyLoads(uw, today.minusWeeks(8));
        List<Bar> bars = new ArrayList<>();
        List<WeekLoad> weeks = LoadMath.weeklyTotals(daily, today, 8);
        for (int i = 0; i < weeks.size(); i++) {
            WeekLoad wk = weeks.get(i);
            String label = i == weeks.size() - 1 ? "Esta" : wk.monday().getDayOfMonth() + " "
                    + wk.monday().getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.forLanguageTag("es")).replace(".", "");
            bars.add(new Bar(label, Math.round(wk.total()), wk.monday().toString()));
        }
        return Widget.weekBars("carga_semanal", "Carga semanal", "RPE × minutos (UA) · línea: tu media de 4 semanas",
                "UA", bars, acwr.map(Acwr::chronicWeekly).orElse(null));
    }

    private List<Insight> insights(WorldContext ctx, Optional<Acwr> acwr, Integer toMatch, List<Integer> matchDays, int dow) {
        List<Insight> out = new ArrayList<>();
        HealthSummary h = ctx.user().health();
        UUID uw = ctx.world().id();
        if (!h.injuredZones().isEmpty()) {
            out.add(Insight.of("🩹", "Cuidamos tus zonas sensibles", "Tu plan evita el alto impacto sobre: "
                    + String.join(", ", h.injuredZones()) + ". Si algo duele durante un ejercicio, cámbialo.", "info"));
        }
        LocalDate today = LocalDate.now(MetricRepository.ZONE);
        if (loads.weeksWithMovements(uw, today.minusDays(6), PREVENTION_KEYS) == 0) {
            out.add(Insight.of("🛡️", "Toca prevención", "Esta semana aún no hiciste nórdicos, Copenhague ni equilibrio: "
                    + "2 sesiones semanales reducen las lesiones de isquiotibiales e ingle.", "warn"));
        } else {
            out.add(Insight.of("🛡️", "Prevención al día", "Ya hiciste trabajo preventivo esta semana. ¡Sigue así!", "good"));
        }
        acwr.ifPresent(a -> out.add(Insight.of("📊", "Tu carga", LoadMath.zoneMessage(a.ratio()),
                LoadMath.zoneTone(LoadMath.zone(a.ratio())))));
        if (toMatch != null) {
            int since = Microcycle.daysSince(dow, matchDays);
            if (toMatch == 0) {
                out.add(Insight.of("⚽", "Día de partido", "Calienta con el protocolo preventivo, hidrátate y al terminar registra minutos y RPE.", "info"));
            } else if (toMatch == 1) {
                out.add(Insight.of("😴", "Mañana juegas", "Sesión corta, duerme bien y prioriza carbohidratos en la cena.", "info"));
            } else if (since == 1) {
                out.add(Insight.of("🧊", "Día después del partido", "Recuperación activa: trote o bici suave, movilidad y rodillo.", "info"));
            }
        }
        out.add(Insight.of("🧭", "Consejo para tu posición", positionTip(ctx.world().stateString("position")), "info"));
        if (h.prudentMode()) {
            out.add(new Insight("🩺", "Modo prudente", h.moderateReason() + ".", "info", "Hacer chequeo", "/health"));
        }
        out.addAll(FootballAdaptations.profileTips(ctx.user().insights()));
        return out;
    }

    // ───────────────────────── Progreso ─────────────────────────

    @Override
    public List<Widget> progress(WorldContext ctx) {
        UUID uw = ctx.world().id();
        LocalDate today = LocalDate.now(MetricRepository.ZONE);
        List<Widget> w = new ArrayList<>();
        Optional<Acwr> acwr = currentAcwr(uw);
        w.add(weeklyLoadWidget(uw, today, acwr));

        // ACWR al cierre de cada semana (fecha real de las sesiones).
        LocalDate first = loads.firstSessionDay(uw);
        List<DayLoad> daily = loads.dailyLoads(uw, today.minusWeeks(12));
        List<Point> acwrPoints = new ArrayList<>();
        LocalDate sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        for (int i = 7; i >= 0; i--) {
            LocalDate at = sunday.minusWeeks(i);
            if (at.isAfter(today)) at = today;
            LocalDate day = at;
            LoadMath.acwr(daily, day, first).ifPresent(a -> acwrPoints.add(new Point(day, a.ratio())));
        }
        if (!acwrPoints.isEmpty()) {
            w.add(Widget.line("acwr", "Relación aguda:crónica", "Zona óptima 0,8–1,3", "", acwrPoints, "up", 1.0));
        }
        addLine(w, uw, "fut_sprint_30m_s", "Sprint de 30 m", "s", "down");
        addLine(w, uw, "fut_salto_cm", "Salto vertical", "cm", "up");
        addLine(w, uw, "fut_dominio_balon", "Dominio de balón (test)", "toques", "up");
        addLine(w, uw, "fut_cooper_m", "Test de Cooper", "m", "up");

        Optional<Double> sprint = metrics.best(uw, "fut_sprint_30m_s", false);
        Optional<Double> jump = metrics.best(uw, "fut_salto_cm", true);
        double touches = Math.max(metrics.best(uw, "fut_dominio_record", true).orElse(0.0),
                metrics.best(uw, "fut_dominio_balon", true).orElse(0.0));
        w.add(Widget.statRow("records", "Tus récords", List.of(
                new Stat("Sprint 30 m", sprint.map(FootballWorld::fmt).orElse("—"), sprint.isPresent() ? "s" : null, null, null),
                new Stat("Salto", jump.map(FootballWorld::fmt).orElse("—"), jump.isPresent() ? "cm" : null, null, null),
                new Stat("Dominadas", touches > 0 ? fmt(touches) : "—", touches > 0 ? "toques" : null, null, null),
                new Stat("Partidos", String.valueOf(loads.matchesLogged(uw)), null, "registrados", null))));

        Map<String, Integer> kinds = loads.kindsSince(uw, today.minusDays(27));
        int max = kinds.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (max > 0) {
            List<Bar> bars = new ArrayList<>();
            kinds.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).forEach(e -> {
                Type t = Type.ofKind(e.getKey());
                bars.add(new Bar(t == null ? "Sesión libre" : t.label, Math.round(e.getValue() * 100.0 / max),
                        e.getValue() + (e.getValue() == 1 ? " sesión" : " sesiones")));
            });
            w.add(Widget.bars("tipos_sesion", "Qué has entrenado (4 semanas)", "Sesiones por tipo", bars));
        } else {
            w.add(Widget.notice("sin_datos", "info", "Aún no hay sesiones",
                    "Registra tus sesiones y partidos con RPE y minutos para ver tu carga, tu ACWR y tus récords."));
        }
        return w;
    }

    private void addLine(List<Widget> w, UUID uw, String metric, String title, String unit, String direction) {
        List<Point> pts = metrics.series(uw, metric, 20);
        if (!pts.isEmpty()) w.add(Widget.line(metric, title, null, unit, pts, direction, null));
    }

    @Override
    public SessionUi sessionUi() {
        return new SessionUi("drills", List.of(), List.of("rpe", "duration"), List.of("reps", "duration"), List.of(
                "Rodilla siempre alineada con el pie al aterrizar, frenar y girar.",
                "Sprints con recuperación completa: si vas cansado, ya no entrenas velocidad.",
                "Registra el RPE y los minutos también de partidos y entrenamientos de equipo: así calculamos tu carga.",
                "En las dominadas de balón, anota en repeticiones tu mejor racha."));
    }

    @Override
    public String assistantPersona(WorldContext ctx) {
        String position = positionName(ctx.world().stateString("position"));
        List<Integer> md = matchDays(ctx);
        String match = md.isEmpty() ? "sin día de partido fijo" : "partido el " + WorldToolkit.dayName(md.get(0)).toLowerCase();
        String acwr = currentAcwr(ctx.world().id()).map(a -> "ACWR actual " + LoadMath.fmt(a.ratio()) + " (zona "
                + LoadMath.zone(a.ratio()) + ")").orElse("ACWR aún sin calcular (menos de 3 semanas de datos)");
        return """
                Eres un preparador físico y técnico de fútbol. Hablas de la semana organizada alrededor del partido
                (MD+1 recuperación, MD-4 fuerza y prevención, MD-3 resistencia, MD-2 velocidad y técnica, MD-1 activación),
                de prevención de lesiones (nórdico de isquiotibiales, Copenhague, equilibrio, calentamiento basado en el
                programa FIFA 11+), de carga interna (RPE × minutos) y de la relación aguda:crónica (zona óptima 0,8–1,3;
                por encima de 1,5 recomienda bajar la intensidad). El usuario juega de %s, %s, sigue la ruta "%s" en nivel
                %d de 5; %s. Adapta los consejos a su posición. Ante dolor agudo, chasquido o inflamación, recomienda parar
                y consultar a un profesional; no diagnostiques lesiones.%s%s
                """.formatted(position.toLowerCase(), match, ctx.world().trackKey(), ctx.world().level(), acwr,
                ctx.user().insights().avoidContact()
                        ? " Toma anticoagulantes: nada de choques, cabezazos ni caídas; sugiere formatos sin contacto." : "",
                ctx.user().insights().heartRateUnreliable()
                        ? " Toma betabloqueadores: habla de intensidad por RPE y prueba del habla, nunca por pulsaciones." : "");
    }

    // ───────────────────────── Utilidades ─────────────────────────

    private List<Integer> matchDays(WorldContext ctx) {
        Double md = ctx.world().stateNumber("matchDay");
        int matches = intState(ctx, "matches", 1);
        if (md == null || matches == 0) return List.of();
        return matches >= 2 ? List.of(md.intValue(), Microcycle.wrap(md.intValue() - 3)) : List.of(md.intValue());
    }

    private static Integer nextMatchDay(int dow, List<Integer> matchDays) {
        return matchDays.stream().min(Comparator.comparingInt(m -> Math.floorMod(m - dow, 7))).orElse(null);
    }

    private static int intState(WorldContext ctx, String key, int fallback) {
        Double v = ctx.world().stateNumber(key);
        return v == null ? fallback : v.intValue();
    }

    static String positionName(String position) {
        return switch (position == null ? "" : position) {
            case "portero" -> "Portero";
            case "defensa" -> "Defensa central";
            case "lateral" -> "Lateral";
            case "extremo" -> "Extremo";
            case "delantero" -> "Delantero";
            case "mediocampista" -> "Mediocampista";
            default -> "Jugador de campo";
        };
    }

    private static String positionRationale(String position) {
        return switch (position == null ? "" : position) {
            case "portero" -> "Portero: reacción, explosividad lateral y blocajes en lugar de mucho volumen aeróbico";
            case "defensa" -> "Defensa central: más fuerza a una pierna y juego aéreo (saltos de cabeceo)";
            case "lateral" -> "Lateral: equilibrio entre sprints largos y resistencia para subir y bajar la banda";
            case "extremo", "delantero" -> "Ataque: más sprints cortos y largos y tiro a portería";
            case "mediocampista" -> "Mediocampista: más capacidad aeróbica (4 × 4 min y más intervalos)";
            default -> "Jugador de campo (sin posición indicada): resistencia, velocidad y técnica equilibradas";
        };
    }

    private static String positionTip(String position) {
        return switch (position == null ? "" : position) {
            case "portero" -> "Trabaja la posición base: peso en la parte delantera del pie para reaccionar antes.";
            case "defensa" -> "En los duelos aéreos, cae siempre sobre dos pies y con las rodillas flexionadas.";
            case "lateral" -> "Tu puesto exige repetir sprints largos: cuida la recuperación entre sesiones.";
            case "extremo" -> "Gana el primer metro: practica salidas con cambio de ritmo y conducción a velocidad.";
            case "delantero" -> "Sprints cortos explosivos y remates con ambas piernas marcan la diferencia.";
            case "campo" -> "Cuéntanos tu posición y ajustamos los ejercicios a lo que te pide tu puesto.";
            default ->"Cubres más distancia que nadie: tu base aeróbica (4 × 4 min) es tu mejor arma.";
        };
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.2f", v).replace('.', ',');
    }
}
