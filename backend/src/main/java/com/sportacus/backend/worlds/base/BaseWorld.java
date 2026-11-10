package com.sportacus.backend.worlds.base;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.catalog.CatalogModels.WorldMovement;
import com.sportacus.backend.world.core.DiscoveryInput;
import com.sportacus.backend.world.core.EnrollmentResult;
import com.sportacus.backend.world.core.EnrollmentResult.GoalSpec;
import com.sportacus.backend.world.core.Factor;
import com.sportacus.backend.world.core.GeneratedPlan;
import com.sportacus.backend.world.core.LoggedSession;
import com.sportacus.backend.world.core.PlanView;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.core.SessionOutcome;
import com.sportacus.backend.world.core.SessionUi;
import com.sportacus.backend.world.core.UserContext;
import com.sportacus.backend.world.core.UserProfileData;
import com.sportacus.backend.world.core.Widget;
import com.sportacus.backend.world.core.Widget.Bar;
import com.sportacus.backend.world.core.Widget.Insight;
import com.sportacus.backend.world.core.Widget.Point;
import com.sportacus.backend.world.core.WorldContext;
import com.sportacus.backend.world.core.WorldModule;
import com.sportacus.backend.world.core.WorldToolkit;
import com.sportacus.backend.world.form.FormSpec;
import com.sportacus.backend.world.form.Forms;
import com.sportacus.backend.world.metrics.MetricRepository;
import com.sportacus.backend.world.session.SessionLogRepository;

/**
 * Mundo 0 "Mi Base": todo usuario queda inscrito al registrarse. Empieza vacío y se llena con lo que el usuario cuenta
 * (Perfil 360). Desde el minuto 1 ofrece un plan prudente de "Movimiento diario" basado en la OMS (2020) y MinSalud
 * ({@link BasePlanBuilder}), registro libre de cualquier actividad y el avance hacia los 150 minutos semanales.
 * El núcleo antepone al inicio los avisos de seguridad, el check-in, el anillo del perfil, una pregunta y las
 * sugerencias de mundos; este módulo solo produce los widgets propios.
 */
@Component
public class BaseWorld implements WorldModule {

    public static final String KEY = "base";
    static final String M_ACTIVE = "base_min_actividad";
    static final String M_VIGOROUS = "base_min_vigorosa";
    static final int VIGOROUS_RPE = 7;

    private final WorldToolkit kit;
    private final MetricRepository metrics;
    private final SessionLogRepository sessionLogs;

    public BaseWorld(WorldToolkit kit, MetricRepository metrics, SessionLogRepository sessionLogs) {
        this.kit = kit;
        this.metrics = metrics;
        this.sessionLogs = sessionLogs;
    }

    @Override
    public String key() {
        return KEY;
    }

    /** La caminata crece cada semana: el núcleo regenera el plan al empezar una semana nueva. */
    @Override
    public boolean planVariesByWeek() {
        return true;
    }

    // ───────────────────────── Onboarding ─────────────────────────

    @Override
    public FormSpec onboardingForm(UserContext user) {
        return Forms.form(KEY, "Mi Base", "No necesitas responder nada: Mi Base se llena con lo que nos cuentas, a tu ritmo.");
    }

    /** Mi Base nunca compite en el test de descubrimiento. */
    @Override
    public List<Factor> discoveryFactors(DiscoveryInput input) {
        return List.of();
    }

    @Override
    public EnrollmentResult enroll(UserContext user, Answers answers) {
        List<GoalSpec> goals = List.of(new GoalSpec("base_oms_150", "150 minutos de actividad a la semana (OMS)",
                Map.of("metric", M_ACTIVE, "weeklyMinutes", BasePlanBuilder.WHO_WEEKLY_MIN, "vigorousCountsDouble", true),
                null));
        List<Factor> why = List.of(Factor.of("oms", 0,
                "Empiezas con un plan suave basado en la OMS: 150 minutos de actividad a la semana y fuerza 2 días"));
        return new EnrollmentResult(null, 1, new LinkedHashMap<>(), new LinkedHashMap<>(), goals, Map.of(), why,
                List.of());
    }

    // ───────────────────────── Plan ─────────────────────────

    @Override
    public GeneratedPlan generatePlan(WorldContext ctx) {
        UserContext user = ctx.user();
        Map<String, String> usable = new LinkedHashMap<>();
        for (WorldMovement m : kit.usableMovements(KEY, user)) usable.put(m.key(), m.name());
        int days = BasePlanBuilder.sessionsPerWeek(user.profile());
        return BasePlanBuilder.build(new BasePlanBuilder.Input(user.profile(), user.health(), user.insights(), usable,
                kit.weekdays(user.profile(), days), weeksActive(ctx)));
    }

    private static int weeksActive(WorldContext ctx) {
        if (ctx.world().enrolledAt() == null) return 0;
        LocalDate since = ctx.world().enrolledAt().atZoneSameInstant(MetricRepository.ZONE).toLocalDate();
        return (int) Math.max(0, ChronoUnit.WEEKS.between(since, LocalDate.now(MetricRepository.ZONE)));
    }

    // ───────────────────────── Evaluación ─────────────────────────

    /** Minutos que cuentan para la meta de la OMS: los intensos valen doble. */
    static double whoMinutes(double active, double vigorous) {
        return active + vigorous;
    }

    static boolean vigorous(Integer rpe) {
        return rpe != null && rpe >= VIGOROUS_RPE;
    }

    /** Tipo de actividad: el título del registro libre o el tipo de sesión del plan. */
    static String activityType(String title, String sessionKind) {
        if (sessionKind != null) {
            return switch (sessionKind) {
                case BasePlanBuilder.WALK -> "Caminar";
                case BasePlanBuilder.STRENGTH, BasePlanBuilder.MIXED -> "Fuerza";
                case BasePlanBuilder.MOBILITY, BasePlanBuilder.GENTLE -> "Yoga o estiramientos";
                default -> title == null ? "Otra" : title;
            };
        }
        return title == null || title.isBlank() ? "Otra" : title.trim();
    }

    /** Mundo que encaja con una actividad que el usuario registra a menudo (o null). */
    static String[] worldFor(String type) {
        if (type == null) return null;
        String t = type.toLowerCase(Locale.ROOT);
        if (t.startsWith("correr")) return new String[] { "running", "Running" };
        if (t.startsWith("fútbol") || t.startsWith("futbol")) return new String[] { "football", "Fútbol" };
        if (t.startsWith("fuerza")) return new String[] { "calisthenics", "Calistenia" };
        return null;
    }

    @Override
    public SessionOutcome evaluateSession(WorldContext ctx, LoggedSession session) {
        SessionOutcome.Builder out = SessionOutcome.builder();
        Integer dur = session.durationMin();
        if (dur == null || dur <= 0) {
            out.highlight("Registra la duración para sumar tus minutos de la semana.");
            return out.build();
        }
        String title = session.id() == null ? null : sessionLogs.recent(ctx.world().id(), 5).stream()
                .filter(s -> s.id().equals(session.id())).map(SessionLogRepository.SessionSummary::title)
                .findFirst().orElse(null);
        String type = activityType(title, session.sessionKind());
        boolean intense = vigorous(session.rpe());
        boolean first = metrics.count(ctx.world().id(), M_ACTIVE) == 0;
        double before = weekWho(ctx);

        Map<String, Object> context = Map.of("tipo", type);
        out.metric(M_ACTIVE, dur, context);
        if (intense) out.metric(M_VIGOROUS, dur, context);
        double after = before + whoMinutes(dur, intense ? dur : 0);
        out.state("lastActivity", type).state("lastActivityOn", session.performedOn().toString());

        if (first) {
            out.achievement("base_primera_actividad", "Primera actividad en Mi Base", "🌱");
            out.highlight("¡Tu primera actividad registrada! Cada minuto cuenta.");
        }
        int target = BasePlanBuilder.WHO_WEEKLY_MIN;
        if (before < target && after >= target) {
            out.achievement("base_semana_oms", "Semana OMS: 150 minutos", "🏅");
            out.highlight("¡Llegaste a los " + target + " minutos de la semana que recomienda la OMS!");
        } else if (after < target) {
            out.highlight("Llevas " + fmt(after) + " de " + target + " minutos esta semana"
                    + (intense ? " (tus minutos intensos cuentan doble)" : "") + ".");
        } else {
            out.highlight("Ya vas en " + fmt(after) + " minutos esta semana: por encima de lo que recomienda la OMS.");
        }
        String[] world = worldFor(type);
        if (world != null && session.planSessionId() == null) {
            double recent = metrics.sumByContextSince(ctx.world().id(), M_ACTIVE, "tipo", 28).getOrDefault(type, 0.0) + dur;
            if (recent >= 90) {
                out.highlight("Te gusta " + type.toLowerCase(Locale.ROOT) + ": el mundo " + world[1]
                        + " podría encajarte cuando quieras un reto.");
            }
        }
        return out.build();
    }

    /** Minutos OMS de la semana en curso (moderados + intensos ×2). */
    private double weekWho(WorldContext ctx) {
        List<Bar> a = metrics.weekly(ctx.world().id(), M_ACTIVE, 1, "sum");
        List<Bar> v = metrics.weekly(ctx.world().id(), M_VIGOROUS, 1, "sum");
        return whoMinutes(a.isEmpty() ? 0 : a.get(0).value(), v.isEmpty() ? 0 : v.get(0).value());
    }

    private List<Bar> whoWeeks(WorldContext ctx, int weeks) {
        List<Bar> a = metrics.weekly(ctx.world().id(), M_ACTIVE, weeks, "sum");
        List<Bar> v = metrics.weekly(ctx.world().id(), M_VIGOROUS, weeks, "sum");
        List<Bar> out = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            double vig = i < v.size() ? v.get(i).value() : 0;
            out.add(new Bar(a.get(i).label(), whoMinutes(a.get(i).value(), vig), a.get(i).detail()));
        }
        return out;
    }

    // ───────────────────────── Dashboard ─────────────────────────

    /** Perfil casi vacío: aún no sabemos ni los días ni el tiempo, y hay muy pocas respuestas del Perfil 360. */
    static boolean nearlyEmpty(UserContext user) {
        UserProfileData p = user.profile();
        return p.daysPerWeek() == null && p.minutesPerSession() == null && user.insights().facts().count() < 5;
    }

    @Override
    public List<Widget> dashboard(WorldContext ctx, PlanView plan) {
        List<Widget> w = new ArrayList<>();
        UserContext user = ctx.user();
        if (nearlyEmpty(user)) {
            w.add(Widget.notice("mi_base", "info", "Te damos la bienvenida a Mi Base",
                    "Este espacio se llena con lo que nos cuentas, a tu ritmo. Mientras tanto, te proponemos un plan suave"
                            + " basado en la OMS y puedes registrar cualquier actividad: todo suma."));
        }
        w.add(Widget.nextSession("Tu próxima sesión", plan == null ? null : plan.nextSession()));

        List<Bar> weeks = whoWeeks(ctx, 6);
        double week = weeks.isEmpty() ? 0 : weeks.get(weeks.size() - 1).value();
        int target = BasePlanBuilder.WHO_WEEKLY_MIN;
        double pct = Math.min(100, Math.round(week * 100.0 / target));
        String detail = week >= target
                ? fmt(week) + " min esta semana: ¡cumpliste la recomendación de la OMS!"
                : fmt(week) + " de " + target + " min esta semana · te faltan " + fmt(target - week)
                        + " · los minutos intensos cuentan doble";
        w.add(Widget.goal("oms_semana", "Tu semana OMS", "150 minutos de actividad a la semana", pct, detail, null));
        w.add(Widget.weekBars("minutos_semana", "Minutos activos por semana", "Meta OMS: 150 min", "min", weeks,
                (double) target));
        w.add(Widget.insights("Para ti", insights(ctx)));
        return w;
    }

    private List<Insight> insights(WorldContext ctx) {
        UserContext user = ctx.user();
        ProfileInsights ins = user.insights();
        HealthSummary h = user.health();
        List<Insight> out = new ArrayList<>();
        out.add(new Insight("➕", "¿Te moviste hoy?",
                "Caminar, bailar, bici, tareas activas… registra cualquier actividad: todo suma a tus 150 minutos.",
                "info", "Registrar actividad", "/log-activity"));

        List<Insight> tips = new ArrayList<>();
        if (ins.readiness() != null && ins.readiness().factor() < 0.85) {
            tips.add(Insight.of("🌤️", ins.readiness().label(), "Según tu check-in de hoy ("
                    + String.join(", ", ins.readiness().reasons()) + "), muévete suave: una caminata corta también cuenta.",
                    "warn"));
        }
        if (h.mustRefer()) {
            tips.add(Insight.of("🩺", "Primero, consulta", "Marcaste señales que un profesional debe revisar. Mientras"
                    + " tanto, tu plan es solo respiración, movilidad suave y caminatas cortas.", "warn"));
        }
        if (ins.hasDog()) {
            tips.add(Insight.of("🐕", "Tu perro, tu compañero",
                    "Como tienes perro: dos paseos de 15 min a paso vivo al día suman 210 min a la semana.", "good"));
        }
        if (ins.sittingHours() != null && ins.sittingHours() >= 8) {
            tips.add(Insight.of("🪑", "Pausas activas", "Como pasas ~" + ins.sittingHours()
                    + " h sentado, levántate 2–3 min cada 30–60 min. La OMS recomienda reducir el tiempo sedentario.", "info"));
        }
        if (ins.activeCommuteMinWeek() != null && ins.activeCommuteMinWeek() > 0) {
            tips.add(Insight.of("🚲", "Tu trayecto cuenta", "Tus ~" + ins.activeCommuteMinWeek()
                    + " min semanales caminando o en bici ya suman a los 150 minutos.", "good"));
        }
        if (ins.youngChildren()) {
            tips.add(Insight.of("🧒", "Muévete con tus hijos",
                    "Como tienes hijos pequeños: bailar, jugar a perseguirse o ir al parque con ellos también cuenta.", "info"));
        }
        if (ins.heatHumid()) {
            tips.add(Insight.of("💧", "Calor y humedad",
                    "Como tu clima es cálido, muévete temprano o al final de la tarde y toma agua antes, durante y después.", "info"));
        }
        if (ins.unsafeAtNight()) {
            tips.add(Insight.of("🌙", "De día o en casa",
                    "Como tu zona no es segura de noche, camina de día o haz la marcha en casa.", "info"));
        }
        if (ins.physicalJob()) {
            tips.add(Insight.of("🧱", "Tu trabajo ya suma",
                    "Como tu trabajo es físico, priorizamos movilidad y fuerza ligera para recuperarte.", "info"));
        }
        if (user.profile().ageOr(0) >= 65) {
            tips.add(Insight.of("⚖️", "Equilibrio",
                    "Desde los 65, la OMS recomienda trabajar el equilibrio 3 días a la semana: siempre con apoyo cerca.", "info"));
        }
        if (h.prudentMode() && !h.mustRefer()) {
            tips.add(new Insight("🛡️", "Modo prudente", h.moderateReason() + ".", "info", "Hacer chequeo", "/health"));
        }
        out.addAll(tips.subList(0, Math.min(3, tips.size())));

        String[] fav = favoriteWorld(ctx);
        if (fav != null) {
            out.add(new Insight("🌍", "El mundo " + fav[1] + " podría encajarte", "Como registras a menudo "
                    + fav[2].toLowerCase(Locale.ROOT) + ", ahí tendrás un plan específico. Mi Base sigue contigo.",
                    "info", "Explorar mundos", "/onboarding/gallery"));
        } else {
            out.add(new Insight("🌍", "Explora los mundos", "Cuando quieras un reto concreto (correr, fuerza, fútbol…),"
                    + " explora los mundos. Mi Base sigue contigo.", "info", "Explorar mundos", "/onboarding/gallery"));
        }
        return out;
    }

    /** {clave, nombre, actividad} del mundo que encaja con la actividad más registrada en 4 semanas (≥ 90 min). */
    private String[] favoriteWorld(WorldContext ctx) {
        Map<String, Double> byType = metrics.sumByContextSince(ctx.world().id(), M_ACTIVE, "tipo", 28);
        String best = null;
        double max = 0;
        for (var e : byType.entrySet()) {
            if (worldFor(e.getKey()) != null && e.getValue() > max) {
                best = e.getKey();
                max = e.getValue();
            }
        }
        if (best == null || max < 90) return null;
        String[] w = worldFor(best);
        return new String[] { w[0], w[1], best };
    }

    // ───────────────────────── Progreso ─────────────────────────

    @Override
    public List<Widget> progress(WorldContext ctx) {
        List<Widget> w = new ArrayList<>();
        UserContext user = ctx.user();
        var uw = ctx.world().id();
        w.add(Widget.weekBars("minutos_oms", "Minutos activos por semana", "Los minutos intensos cuentan doble (OMS)",
                "min", whoWeeks(ctx, 8), (double) BasePlanBuilder.WHO_WEEKLY_MIN));

        Map<String, Double> byType = metrics.sumByContextSince(uw, M_ACTIVE, "tipo", 28);
        if (!byType.isEmpty()) {
            double max = byType.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
            List<Bar> bars = byType.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .map(e -> new Bar(e.getKey(), Math.round(e.getValue() * 100 / Math.max(1, max)), fmt(e.getValue()) + " min"))
                    .toList();
            w.add(Widget.bars("tipos", "Tus actividades", "Últimas 4 semanas", bars));
        }
        w.add(Widget.weekBars("sesiones", "Actividades por semana", null, "actividades",
                metrics.weekly(uw, M_ACTIVE, 8, "count"), (double) BasePlanBuilder.sessionsPerWeek(user.profile())));

        addUserLine(w, user, "sueno", "bienestar_sueno_h", "Horas de sueño", "Del check-in diario (meta 7–9 h)", "h", "up", 7.0);
        addUserLine(w, user, "animo", "bienestar_animo", "Ánimo", "Del check-in diario (1–5)", "/5", "up", null);
        addUserLine(w, user, "energia", "bienestar_energia", "Energía", "Del check-in diario (1–5)", "/5", "up", null);
        addUserLine(w, user, "estres", "bienestar_estres", "Estrés", "Del check-in diario (1–5)", "/5", "down", null);
        String weightPref = weightView(user.insights());
        if ("si".equals(weightPref)) {
            addUserLine(w, user, "peso", "peso_kg", "Peso", "Lo que registras en tu perfil", "kg", null, null);
        } else if ("solo_tendencia".equals(weightPref)) {
            List<Point> pts = metrics.seriesForUser(user.userId(), "peso_kg", 30);
            if (pts.size() >= 2) {
                w.add(Widget.line("peso", "Tendencia de tu peso", "Cambio desde tu primer registro, sin cifras absolutas",
                        "kg", relativeTrend(pts), null, null));
            }
        }
        if (metrics.count(uw, M_ACTIVE) == 0) {
            w.add(Widget.notice("sin_datos", "info", "Aún no hay actividades",
                    "Registra cualquier actividad o haz una sesión del plan para ver aquí tu avance. El check-in diario"
                            + " suma tus tendencias de sueño, ánimo y energía."));
        }
        return w;
    }

    /**
     * Cómo quiere ver el peso: "si" (por defecto), "solo_tendencia" o "no" (pregunta {@code rc_ver_peso}). Con señales de
     * relación difícil con la comida nunca se muestra.
     */
    static String weightView(ProfileInsights ins) {
        if (ins.eatingCaution()) return "no";
        String pref = ins.facts().string("rc_ver_peso");
        return "no".equals(pref) || "solo_tendencia".equals(pref) ? pref : "si";
    }

    /** Serie expresada como cambio respecto al primer punto (sin valores absolutos). */
    static List<Point> relativeTrend(List<Point> pts) {
        double first = pts.get(0).value();
        return pts.stream().map(p -> new Point(p.date(), Math.round((p.value() - first) * 10) / 10.0)).toList();
    }

    private void addUserLine(List<Widget> w, UserContext user, String key, String metric, String title,
            String subtitle, String unit, String direction, Double target) {
        List<Point> pts = metrics.seriesForUser(user.userId(), metric, 30);
        if (!pts.isEmpty()) w.add(Widget.line(key, title, subtitle, unit, pts, direction, target));
    }

    // ───────────────────────── Reproductor y asistente ─────────────────────────

    @Override
    public SessionUi sessionUi() {
        return new SessionUi("drills", List.of(), List.of("duration", "rpe", "mood"), List.of("reps", "duration"), List.of(
                "Ritmo moderado: puedes hablar, pero no cantar.",
                "Algo de actividad es mejor que nada: si hoy solo puedes 10 minutos, también cuentan.",
                "Si sientes dolor en el pecho, mareo o falta de aire, detente."));
    }

    @Override
    public String assistantPersona(WorldContext ctx) {
        UserContext user = ctx.user();
        double week = weekWho(ctx);
        return """
                Eres el coach de "Mi Base", el espacio de inicio de Sportacus: cercano, cálido y sin presión, para personas
                que están empezando o retomando la actividad física. Hablas en español neutro latino y tuteas.
                Te basas en las directrices de la OMS (2020) y de MinSalud Colombia: 150–300 minutos de actividad moderada
                a la semana (los intensos cuentan doble), fortalecer los músculos 2 días y, desde los 65 años, equilibrio.
                Celebra cualquier avance; "algo es mejor que nada". Nunca culpes ni presiones, nunca diagnostiques ni
                interpretes síntomas ni sugieras medicamentos; ante dolor en el pecho, mareo, falta de aire o dolor intenso,
                recomienda detenerse y consultar a un profesional.
                Si el usuario muestra interés claro por algo concreto (correr, fuerza, fútbol, rehabilitación), sugiérele
                explorar el mundo correspondiente, sin insistir. Cuando no sepas un dato, puedes invitarlo a contarlo en su
                perfil, explicando para qué sirve. Esta semana lleva %s de 150 minutos OMS.%s%s
                """.formatted(fmt(week),
                user.health().mustRefer()
                        ? "\nIMPORTANTE: marcó señales de alarma; su plan es solo suave e insiste en que consulte antes de progresar."
                        : "",
                user.health().prudentMode() && !user.health().mustRefer()
                        ? "\nAún no hizo el chequeo de salud: mantén la intensidad moderada y sin impacto alto."
                        : "");
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }
}
