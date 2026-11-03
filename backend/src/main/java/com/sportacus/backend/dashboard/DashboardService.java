package com.sportacus.backend.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.dashboard.DashboardQueryRepository.ExerciseLoad;
import com.sportacus.backend.dashboard.DashboardQueryRepository.ProfileExtras;
import com.sportacus.backend.dashboard.DashboardRules.InsightInput;
import com.sportacus.backend.dashboard.dto.CurrentActivityResponse;
import com.sportacus.backend.dashboard.dto.DashboardParts.FocusItem;
import com.sportacus.backend.dashboard.dto.DashboardParts.FocusResponse;
import com.sportacus.backend.dashboard.dto.DashboardParts.MetricResponse;
import com.sportacus.backend.dashboard.dto.DashboardParts.ScorePart;
import com.sportacus.backend.dashboard.dto.DashboardParts.TodayResponse;
import com.sportacus.backend.dashboard.dto.DashboardResponse;
import com.sportacus.backend.dashboard.dto.ProgressItemResponse;
import com.sportacus.backend.dashboard.dto.RadarPointResponse;
import com.sportacus.backend.dashboard.dto.StatsResponse;
import com.sportacus.backend.questionnaire.QuestionnaireService;
import com.sportacus.backend.questionnaire.dto.QuestionnaireSummaryResponse;
import com.sportacus.backend.recommendation.RecommendationRepository;
import com.sportacus.backend.recommendation.RecommendationRepository.UserContext;
import com.sportacus.backend.recommendation.RecommendationService;
import com.sportacus.backend.recommendation.dto.RoutineDayResponse;
import com.sportacus.backend.recommendation.dto.RoutineResponse;
import com.sportacus.backend.user.User;
import com.sportacus.backend.user.UserNameRepository;

/**
 * Dashboard v2: todo se calcula en vivo con el perfil, la rutina vigente y el checklist (no con sesiones
 * registradas). El gráfico principal ({@code focus}) cambia según la meta del usuario; los indicadores y consejos
 * salen de datos reales y, si falta un dato, se informa en lugar de inventarlo.
 */
@Service
public class DashboardService {

    private static final String COLOR_STRENGTH = "#4CAF50";
    private static final String COLOR_CARDIO = "#2196F3";
    private static final String COLOR_MOBILITY = "#FF9800";
    private static final double SECONDS_PER_REP = 3.0;
    private static final int DEFAULT_WORK_SECONDS = 30;
    private static final int DEFAULT_REST_SECONDS = 60;

    /** Zonas del radar (clave, etiqueta) en el orden en que se dibujan. */
    private static final Map<String, String> MUSCLE_LABELS = new LinkedHashMap<>();
    static {
        MUSCLE_LABELS.put("pecho", "Pecho");
        MUSCLE_LABELS.put("hombro", "Hombro");
        MUSCLE_LABELS.put("espalda", "Espalda");
        MUSCLE_LABELS.put("pierna", "Pierna");
        MUSCLE_LABELS.put("core", "Core");
        MUSCLE_LABELS.put("brazo", "Brazo");
    }

    /** Grupo muscular del catálogo -> zona del radar. */
    private static final Map<String, String> MUSCLE_BUCKET = Map.ofEntries(
            Map.entry("Pecho", "pecho"), Map.entry("Hombros", "hombro"), Map.entry("Espalda", "espalda"),
            Map.entry("Glúteos", "pierna"), Map.entry("Cuádriceps", "pierna"), Map.entry("Isquiotibiales", "pierna"),
            Map.entry("Pantorrillas", "pierna"), Map.entry("Aductores", "pierna"), Map.entry("Core", "core"),
            Map.entry("Bíceps", "brazo"), Map.entry("Tríceps", "brazo"), Map.entry("Antebrazos", "brazo"));

    private final DashboardQueryRepository queries;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationService recommendationService;
    private final QuestionnaireService questionnaireService;
    private final UserNameRepository userNameRepository;

    public DashboardService(DashboardQueryRepository queries, RecommendationRepository recommendationRepository,
            RecommendationService recommendationService, QuestionnaireService questionnaireService,
            UserNameRepository userNameRepository) {
        this.queries = queries;
        this.recommendationRepository = recommendationRepository;
        this.recommendationService = recommendationService;
        this.questionnaireService = questionnaireService;
        this.userNameRepository = userNameRepository;
    }

    /** Resumen de la rutina vigente que usan los gráficos. */
    private record RoutineView(RoutineResponse routine, List<ExerciseLoad> loads, Map<String, Integer> setsByMuscle,
            Map<String, Double> minutesByKind, double totalMinutes, String category) {
    }

    public DashboardResponse buildDashboard(User user) {
        UUID userId = user.getId();
        UserContext ctx = recommendationRepository.loadUserContext(userId);
        ProfileExtras extras = queries.profileExtras(userId);
        int strength = questionnaireService.profileStrengthPct(userId);
        RoutineView view = loadRoutine(userId);

        int doneCount = view == null ? 0 : view.routine().doneCount();
        int totalCount = view == null ? 0 : view.routine().totalCount();
        int checklistPct = DashboardRules.pct(doneCount, totalCount);
        int affinity = view == null ? 0 : view.routine().affinityScore();

        String goal = ctx.primaryGoal();
        int musclesWithSets = view == null ? 0 : (int) view.setsByMuscle().values().stream().filter(v -> v > 0).count();
        FocusResponse focus = buildFocus(DashboardRules.focusType(goal, musclesWithSets), ctx, extras, view, strength);

        List<ScorePart> parts = List.of(
                new ScorePart("profile", "Fuerza del perfil", strength, DashboardRules.WEIGHT_STRENGTH),
                new ScorePart("routine", "Afinidad de tu rutina", affinity, DashboardRules.WEIGHT_AFFINITY),
                new ScorePart("checklist", "Checklist de la rutina", checklistPct, DashboardRules.WEIGHT_CHECKLIST));
        int globalScore = DashboardRules.globalScore(strength, affinity, checklistPct);

        StatsResponse stats = new StatsResponse(globalScore, strength, startedTemplates(userId));

        TodayResponse today = view == null ? null
                : new TodayResponse(view.routine().activityName(), doneCount, totalCount, checklistPct);

        Integer daysPerWeek = ctx.daysPerWeek() != null ? ctx.daysPerWeek()
                : (ctx.preferredDays().isEmpty() ? null : ctx.preferredDays().size());
        List<MetricResponse> metrics = new ArrayList<>();
        addIfPresent(metrics, DashboardRules.bmiMetric(ctx.bmi()));
        addIfPresent(metrics, DashboardRules.sleepMetric(ctx.sleepHours()));
        addIfPresent(metrics, DashboardRules.waterMetric(extras.waterLiters()));
        addIfPresent(metrics, DashboardRules.daysMetric(daysPerWeek));
        addIfPresent(metrics, DashboardRules.sessionTimeMetric(ctx.timePerSession()));

        InsightInput insightInput = new InsightInput(ctx.hasActiveInjury(), ctx.sleepHours(), ctx.stressLevel(),
                extras.waterLiters(), ctx.bmi(), strength, view != null, doneCount, totalCount);

        return new DashboardResponse(
                userNameRepository.displayNameOf(user), stats, radarFrom(view), compositionProgress(view),
                currentActivity(view), goal, DashboardRules.goalLabel(goal), focus, today, metrics,
                DashboardRules.insights(insightInput), strength, parts);
    }

    // ───────────────────────── Rutina vigente ─────────────────────────

    /** Rutina vigente (se genera si aún no existe, igual que GET /api/routine) o null si el catálogo está vacío. */
    private RoutineView loadRoutine(UUID userId) {
        RoutineResponse routine;
        try {
            routine = recommendationService.getRoutine(userId);
        } catch (ResponseStatusException noCatalog) {
            return null;
        }
        List<ExerciseLoad> loads = queries.routeLoads(UUID.fromString(routine.routeId()));

        Map<String, Integer> setsByMuscle = new LinkedHashMap<>();
        MUSCLE_LABELS.keySet().forEach(k -> setsByMuscle.put(k, 0));
        Map<String, Double> minutesByKind = new LinkedHashMap<>();
        minutesByKind.put("fuerza", 0.0);
        minutesByKind.put("cardio", 0.0);
        minutesByKind.put("movilidad", 0.0);
        Map<String, Integer> categoryCount = new LinkedHashMap<>();
        double total = 0;

        for (ExerciseLoad load : loads) {
            String bucket = load.muscleGroup() == null ? null : MUSCLE_BUCKET.get(load.muscleGroup());
            int sets = load.sets() == null ? 1 : load.sets();
            if (bucket != null) {
                setsByMuscle.merge(bucket, sets, Integer::sum);
            }
            double minutes = estimateMinutes(load);
            minutesByKind.merge(kindOf(load.category()), minutes, Double::sum);
            total += minutes;
            categoryCount.merge(load.category(), 1, Integer::sum);
        }
        String category = categoryCount.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        return new RoutineView(routine, loads, setsByMuscle, minutesByKind, total, category);
    }

    /** Tiempo estimado del ejercicio: series de trabajo (3 s por repetición o duración fija) más descansos. */
    static double estimateMinutes(ExerciseLoad load) {
        int sets = load.sets() == null ? 1 : load.sets();
        double workSec = load.durationSec() != null ? load.durationSec()
                : (load.reps() != null ? load.reps() * SECONDS_PER_REP : DEFAULT_WORK_SECONDS);
        int rest = load.restSec() == null ? DEFAULT_REST_SECONDS : load.restSec();
        return (sets * workSec + Math.max(0, sets - 1) * rest) / 60.0;
    }

    /** Categoría del catálogo -> fuerza | cardio | movilidad. */
    static String kindOf(String category) {
        if (category == null) {
            return "fuerza";
        }
        return switch (category) {
            case "Cardiovascular", "Deportes" -> "cardio";
            case "Flexibilidad", "Mente-Cuerpo", "Rehabilitación" -> "movilidad";
            default -> "fuerza";
        };
    }

    // ───────────────────────── Campos de compatibilidad v1 ─────────────────────────

    /** Radar v1 = énfasis muscular de la rutina: la zona con más series vale 100 y el resto es relativo. */
    private List<RadarPointResponse> radarFrom(RoutineView view) {
        List<RadarPointResponse> points = new ArrayList<>();
        int max = view == null ? 0 : view.setsByMuscle().values().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (Map.Entry<String, String> entry : MUSCLE_LABELS.entrySet()) {
            int sets = view == null ? 0 : view.setsByMuscle().get(entry.getKey());
            points.add(new RadarPointResponse(entry.getKey(), entry.getValue(), DashboardRules.pct(sets, max)));
        }
        return points;
    }

    /** Progress v1 = composición de la rutina por tipo (porcentaje del tiempo estimado). */
    private List<ProgressItemResponse> compositionProgress(RoutineView view) {
        return List.of(
                new ProgressItemResponse("fuerza", "Fuerza", "💪", COLOR_STRENGTH, sharePct(view, "fuerza")),
                new ProgressItemResponse("cardio", "Cardio", "🫀", COLOR_CARDIO, sharePct(view, "cardio")),
                new ProgressItemResponse("movilidad", "Movilidad", "🧘", COLOR_MOBILITY, sharePct(view, "movilidad")));
    }

    private int sharePct(RoutineView view, String kind) {
        return view == null ? 0 : DashboardRules.pct(view.minutesByKind().get(kind), view.totalMinutes());
    }

    private CurrentActivityResponse currentActivity(RoutineView view) {
        if (view == null) {
            return new CurrentActivityResponse("Sin rutina", "Completa tu perfil para recibir una rutina", false);
        }
        int n = view.loads().size();
        return new CurrentActivityResponse(view.routine().activityName(),
                "Tu rutina vigente · " + n + (n == 1 ? " ejercicio" : " ejercicios"), false);
    }

    /** Plantillas de perfil con al menos una respuesta ("módulos activos"). */
    private int startedTemplates(UUID userId) {
        return (int) questionnaireService.listQuestionnaires(userId).groups().stream()
                .flatMap(g -> g.items().stream())
                .map(QuestionnaireSummaryResponse::status)
                .filter(status -> !"sin_iniciar".equals(status))
                .count();
    }

    // ───────────────────────── Gráfico principal ─────────────────────────

    private FocusResponse buildFocus(String type, UserContext ctx, ProfileExtras extras, RoutineView view, int strength) {
        boolean wellbeing = DashboardRules.RADAR.equals(type) && !isMuscleGoal(ctx.primaryGoal());
        return switch (type) {
            case DashboardRules.RADAR -> wellbeing ? wellbeingFocus(ctx, extras, view) : muscleFocus(view);
            case DashboardRules.DONUT -> compositionFocus(view, ctx.primaryGoal());
            case DashboardRules.BARS -> constancyFocus(view);
            default -> gaugeFocus(strength, ctx.primaryGoal() == null);
        };
    }

    private boolean isMuscleGoal(String goal) {
        return "ganancia_muscular".equals(goal) || "rendimiento_deportivo".equals(goal);
    }

    /** Ganancia muscular / rendimiento: énfasis (series) por zona muscular de la rutina. */
    private FocusResponse muscleFocus(RoutineView view) {
        int totalSets = view == null ? 0 : view.setsByMuscle().values().stream().mapToInt(Integer::intValue).sum();
        int max = view == null ? 0 : view.setsByMuscle().values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<FocusItem> items = new ArrayList<>();
        for (Map.Entry<String, String> entry : MUSCLE_LABELS.entrySet()) {
            int sets = view == null ? 0 : view.setsByMuscle().get(entry.getKey());
            items.add(new FocusItem(entry.getKey(), entry.getValue(), DashboardRules.pct(sets, max),
                    sets + (sets == 1 ? " serie" : " series"), null));
        }
        String subtitle = totalSets == 0
                ? "Tu rutina todavía no tiene series de fuerza"
                : totalSets + " series en tu rutina · la zona más trabajada vale 100";
        return new FocusResponse(DashboardRules.RADAR, "Énfasis muscular de tu rutina", subtitle, items);
    }

    /** Pérdida de grasa (o meta muscular sin fuerza suficiente): reparto cardio/fuerza/movilidad y minutos semanales. */
    private FocusResponse compositionFocus(RoutineView view, String goal) {
        List<FocusItem> items = new ArrayList<>();
        String[][] kinds = { { "cardio", "Cardio", COLOR_CARDIO }, { "fuerza", "Fuerza", COLOR_STRENGTH },
                { "movilidad", "Movilidad", COLOR_MOBILITY } };
        for (String[] kind : kinds) {
            double minutes = view == null ? 0 : view.minutesByKind().get(kind[0]);
            items.add(new FocusItem(kind[0], kind[1], sharePct(view, kind[0]),
                    Math.round(minutes) + " min/semana", kind[2]));
        }
        long weekly = view == null ? 0 : Math.round(view.totalMinutes());
        int target = DashboardRules.WEEKLY_MINUTES_TARGET;
        String title = "perdida_grasa".equals(goal) ? "Cómo se reparte tu gasto" : "Composición de tu rutina";
        String subtitle = view == null
                ? "Aún no hay rutina para calcular"
                : "≈ " + weekly + " min/semana de " + target + " recomendados (" + DashboardRules.pct(weekly, target) + " %)";
        return new FocusResponse(DashboardRules.DONUT, title, subtitle, items);
    }

    /** Bienestar / flexibilidad / estrés / rehabilitación: seis ejes derivados del perfil. */
    private FocusResponse wellbeingFocus(UserContext ctx, ProfileExtras extras, RoutineView view) {
        int mobilityShare = sharePct(view, "movilidad");
        List<FocusItem> items = List.of(
                axis("sleep", "Sueño", DashboardRules.sleepScore(ctx.sleepHours(), ctx.sleepQuality()), "Salud y hábitos",
                        ctx.sleepHours() == null ? null : DashboardRules.hoursText(ctx.sleepHours())),
                axis("calm", "Calma", DashboardRules.calmScore(ctx.stressLevel()), "Salud y hábitos",
                        ctx.stressLevel() == null ? null : "Estrés " + ctx.stressLevel() + "/5"),
                axis("hydration", "Hidratación", DashboardRules.hydrationScore(extras.waterLiters()), "Alimentación",
                        extras.waterLiters() == null ? null : DashboardRules.litersText(extras.waterLiters())),
                axis("activity", "Actividad diaria",
                        DashboardRules.dailyActivityScore(extras.dailySteps(), extras.workActivity()), "Experiencia y actividad",
                        extras.dailySteps() == null ? extras.workActivity() : extras.dailySteps() + " pasos"),
                new FocusItem("mobility", "Movilidad", view == null ? 0 : DashboardRules.mobilityScore(mobilityShare),
                        view == null ? "Sin rutina todavía" : mobilityShare + " % de tu rutina", null),
                axis("nutrition", "Nutrición", DashboardRules.nutritionScore(extras.mealsPerDay()), "Alimentación",
                        extras.mealsPerDay() == null ? null : extras.mealsPerDay() + " comidas/día"));
        long filled = items.stream().filter(i -> i.value() > 0).count();
        String subtitle = filled < 3
                ? "Completa tus plantillas para ver tu bienestar completo"
                : "Calculado con tus datos de sueño, estrés, hidratación, actividad y nutrición";
        return new FocusResponse(DashboardRules.RADAR, "Tu equilibrio de bienestar", subtitle, items);
    }

    private FocusItem axis(String key, String label, Integer value, String template, String detail) {
        if (value == null) {
            return new FocusItem(key, label, 0, "Falta completar " + template, null);
        }
        return new FocusItem(key, label, value, detail, null);
    }

    /** Hábito activo: constancia por día de la semana (porcentaje del checklist de cada día completado). */
    private FocusResponse constancyFocus(RoutineView view) {
        List<FocusItem> items = new ArrayList<>();
        if (view != null) {
            for (RoutineDayResponse day : view.routine().days()) {
                int total = day.exercises().size();
                long done = day.exercises().stream().filter(e -> e.done()).count();
                items.add(new FocusItem(day.dayLabel(), day.dayLabel(), DashboardRules.pct(done, total),
                        done + "/" + total + " ejercicios", null));
            }
        }
        String subtitle = view == null
                ? "Aún no hay rutina para calcular"
                : view.routine().doneCount() + " de " + view.routine().totalCount() + " ejercicios completados esta semana";
        return new FocusResponse(DashboardRules.BARS, "Tu constancia semanal", subtitle, items);
    }

    /** Sin meta definida: medidor de la fuerza del perfil. */
    private FocusResponse gaugeFocus(int strength, boolean noGoal) {
        String detail = noGoal ? "Falta completar Objetivo y motivación" : "Plantillas de perfil completadas";
        return new FocusResponse(DashboardRules.GAUGE, "Fuerza de tu perfil",
                noGoal ? "Define tu objetivo para personalizar este panel" : "Cuantos más datos, más afinada tu rutina",
                List.of(new FocusItem("strength", "Perfil", strength, detail, COLOR_STRENGTH)));
    }

    private void addIfPresent(List<MetricResponse> list, MetricResponse metric) {
        if (metric != null) {
            list.add(metric);
        }
    }
}
