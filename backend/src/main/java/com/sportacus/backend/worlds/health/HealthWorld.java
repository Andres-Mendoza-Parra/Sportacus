package com.sportacus.backend.worlds.health;

import static com.sportacus.backend.world.form.Forms.multi;
import static com.sportacus.backend.world.form.Forms.no;
import static com.sportacus.backend.world.form.Forms.opt;
import static com.sportacus.backend.world.form.Forms.range;
import static com.sportacus.backend.world.form.Forms.showIf;
import static com.sportacus.backend.world.form.Forms.single;
import static com.sportacus.backend.world.form.Forms.yes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
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
import com.sportacus.backend.world.catalog.CatalogModels.AssessmentInfo;
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
import com.sportacus.backend.world.core.Widget.Bar;
import com.sportacus.backend.world.core.Widget.Insight;
import com.sportacus.backend.world.core.Widget.Phase;
import com.sportacus.backend.world.core.Widget.Zone;
import com.sportacus.backend.world.core.WorldContext;
import com.sportacus.backend.world.core.WorldModule;
import com.sportacus.backend.world.core.WorldToolkit;
import com.sportacus.backend.world.form.FormSpec;
import com.sportacus.backend.world.form.Forms;
import com.sportacus.backend.world.metrics.MetricRepository;
import com.sportacus.backend.world.session.SessionLogRepository;
import com.sportacus.backend.world.session.SessionLogRepository.SessionSummary;
import com.sportacus.backend.worlds.health.PainMonitoring.Criteria;
import com.sportacus.backend.worlds.health.PainMonitoring.Decision;
import com.sportacus.backend.worlds.health.PainMonitoring.Light;

/**
 * Mundo Salud y Rehabilitación: programas EDUCATIVOS de ejercicio general por fases (no diagnostica ni trata).
 * <ul>
 * <li>Una progresión {@code hea_<ruta>} por ruta; cada escalón es una fase con su prescripción y criterio de avance.</li>
 * <li>Semáforo de dolor por sesión ({@link PainMonitoring}, Silbernagel 2007 en versión conservadora): verde suma
 * para avanzar, amarillo mantiene y baja volumen, rojo retrocede una fase.</li>
 * <li>Banderas rojas → solo una sesión mínima de respiración y movilidad; postoperatorio sin aval → fase 1 fija.</li>
 * <li>Tests CDC STEADI (dominio público): 30 s silla, TUG, equilibrio en 4 etapas.</li>
 * </ul>
 * Fuentes: docs/17 §1 (Salud) y §4.
 */
@Component
public class HealthWorld implements WorldModule {

    public static final String KEY = "health";
    static final String DISCLAIMER = "Este programa es educativo y no reemplaza la valoración de un profesional de la salud.";
    static final String SESSION_NOTE = "Dolor permitido: hasta 3/10 durante y después. Si sube a 4–5, reduce el rango o el ritmo; "
            + "si pasa de 5 o es punzante, detente. Mañana te preguntaremos cómo amaneciste.";
    static final Set<String> FALL_TESTS = Set.of("hea_test_silla_30s", "hea_test_tug", "hea_test_equilibrio_4");
    private static final String[] LETTERS = { "A", "B", "C", "D", "E" };

    private final WorldToolkit kit;
    private final MetricRepository metrics;
    private final SessionLogRepository sessionLogs;

    public HealthWorld(WorldToolkit kit, MetricRepository metrics, SessionLogRepository sessionLogs) {
        this.kit = kit;
        this.metrics = metrics;
        this.sessionLogs = sessionLogs;
    }

    @Override
    public String key() {
        return KEY;
    }

    // ───────────────────────── Onboarding ─────────────────────────

    @Override
    public FormSpec onboardingForm(UserContext user) {
        FormSpec form = Forms.form("health", "Tu programa de salud",
                "Con esto elegimos tu ruta y la fase de inicio. " + DISCLAIMER,
                single("h_reason", "¿Qué te trae a este mundo?", null,
                        opt("dolor_sin_lesion", "🤕", "Tengo dolor sin una lesión clara"),
                        opt("lesion_reciente", "🩹", "Una lesión reciente (torcedura, golpe, sobrecarga)"),
                        opt("postoperatorio", "🏥", "Me operaron y quiero recuperarme"),
                        opt("cronica", "⏳", "Una condición de larga duración (artrosis, lumbalgia, hipertensión…)"),
                        opt("prevencion_edad", "🧓", "Prevenir, mantenerme fuerte con la edad"),
                        opt("postura", "🪑", "Postura y muchas horas sentado")),
                single("h_zone", "¿Qué zona quieres trabajar?", "Si tu molestia no está aquí, elige «Ninguna en particular».",
                        opt("rodilla", "🦵", "Rodilla"), opt("cadera", "🦴", "Cadera"),
                        opt("lumbar", "🔻", "Espalda baja"), opt("hombro", "💪", "Hombro"),
                        opt("tobillo", "🦶", "Tobillo o pie"), opt("ninguna", "🧍", "Ninguna en particular")),
                showIf(single("h_onset", "¿Hace cuánto empezó la molestia (o fue la cirugía)?", null,
                        opt("menos_6s", "🆕", "Menos de 6 semanas"),
                        opt("6s_3m", "📅", "Entre 6 semanas y 3 meses"),
                        opt("mas_3m", "🗓️", "Más de 3 meses")),
                        "h_reason", "dolor_sin_lesion", "lesion_reciente", "postoperatorio", "cronica"),
                range("h_pain_rest", "¿Cuánto dolor tienes ahora, en reposo?", "0 = nada · 10 = el peor imaginable", 0, 10, 1, "/10"),
                range("h_pain_activity", "¿Y cuánto dolor tienes al moverte o en tus actividades?", "0 = nada · 10 = el peor imaginable", 0, 10, 1, "/10"),
                single("h_guided", "¿Te está guiando un profesional de la salud (médico, fisioterapeuta)?", null,
                        yes("Sí"), no("No")),
                showIf(single("h_postop_clearance", "¿Tu médico o fisioterapeuta te autorizó a hacer ejercicio?", null,
                        yes("Sí, tengo su autorización"), no("Todavía no")), "h_reason", "postoperatorio"),
                multi("h_limits", "¿Qué te cuesta hoy por tu molestia?", "Elige todas las que apliquen.",
                        opt("escaleras", "🪜", "Subir escaleras"), opt("silla", "🪑", "Levantarme de la silla"),
                        opt("caminar_15", "🚶", "Caminar 15 minutos"), opt("cargar_bolsas", "🛍️", "Cargar bolsas"),
                        opt("dormir", "😴", "Dormir"), opt("ninguna", "✅", "Nada de esto")),
                single("h_falls", "¿Te has caído en el último año, te sientes inestable al caminar o te preocupa caerte?",
                        "Preguntas clave de CDC STEADI para detectar riesgo de caídas.", yes("Sí"), no("No")));
        Map<String, Object> prefill = new LinkedHashMap<>();
        String zone = HealthTriage.zoneFromHealth(user.health().injuredZones());
        if (zone != null) prefill.put("h_zone", zone);
        if (user.health().hasFlag("cirugia_reciente")) prefill.put("h_reason", "postoperatorio");
        return prefill.isEmpty() ? form : form.withAnswers(prefill);
    }

    @Override
    public List<Factor> discoveryFactors(DiscoveryInput in) {
        List<Factor> f = new ArrayList<>();
        Answers a = in.answers();
        HealthSummary h = in.user().health();
        int age = in.user().profile().ageOr(30);
        boolean issues = !h.injuredZones().isEmpty() || h.hasAny("hipertension", "diabetes", "cardiopatia", "renal",
                "osteoporosis", "artritis", "postoperatorio") || h.hasFlag("cirugia_reciente");
        if (h.mustRefer()) f.add(Factor.of("seguridad", 30, "Marcaste señales que un profesional debe revisar: aquí empiezas de forma segura y suave"));
        if (a.contains("d_motivation", "salud")) f.add(Factor.of("motivacion", 25, "Quieres recuperarte o cuidar tu salud: es justo lo que hace este mundo"));
        if ("sensaciones".equals(a.string("d_motivator"))) f.add(Factor.of("motivador", 10, "Te motiva sentirte mejor y con menos molestias"));
        if ("suave".equals(a.string("d_intensity"))) f.add(Factor.of("intensidad", 10, "Prefieres algo suave y controlado"));
        if (a.contains("d_experience", "rehabilitacion")) f.add(Factor.of("experiencia", 8, "Ya conoces la fisioterapia o la rehabilitación"));
        if (a.contains("d_enjoy", "suave")) f.add(Factor.of("gusto", 6, "Disfrutas los movimientos suaves"));
        if (!h.injuredZones().isEmpty()) {
            f.add(Factor.of("lesion", 12, "Tienes molestias en: " + String.join(", ", h.injuredZones()) + "; aquí hay un protocolo por fases para eso"));
        }
        if (h.hasAny("hipertension", "diabetes", "cardiopatia", "renal", "osteoporosis", "artritis")) {
            f.add(Factor.of("condicion", 10, "Tienes una condición crónica que mejora con actividad bien dosificada"));
        }
        if (h.hasFlag("cirugia_reciente")) f.add(Factor.of("cirugia", 10, "Tras una cirugía conviene volver al movimiento poco a poco"));
        if (age >= 60) f.add(Factor.of("edad", age >= 65 ? 12 : 8, "Fuerza y equilibrio son clave para prevenir caídas a partir de los 60"));
        if ("alto".equals(h.riskLevel())) f.add(Factor.of("riesgo", 10, "Tu chequeo indica que conviene empezar con un programa controlado"));
        else if ("moderado".equals(h.riskLevel())) f.add(Factor.of("riesgo", 6, "Tu chequeo sugiere empezar con cuidado"));
        if ("intensa".equals(a.string("d_intensity"))) f.add(Factor.of("intensidad", -10, "Buscas intensidad alta y aquí las sesiones son suaves"));
        if (!issues && !h.mustRefer() && age < 60) {
            if (a.contains("d_motivation", "equipo")) f.add(Factor.of("motivacion_equipo", -8, "Sin molestias ni condiciones, un deporte en equipo te motivará más"));
            if (a.contains("d_motivation", "fuerza_fisico")) f.add(Factor.of("motivacion_fuerza", -8, "Sin molestias ni condiciones, otros mundos te retarán más en fuerza"));
        }
        return f;
    }

    /** Postoperatorio sin aval: solo la fase 1 hasta confirmar la autorización (POST /health/clearance). */
    static boolean postOpLocked(HealthSummary h, Answers profile) {
        boolean reasonPostOp = "postoperatorio".equals(profile.string("h_reason"));
        boolean screenedSurgery = h.hasFlag("cirugia_reciente");
        if (!reasonPostOp && !screenedSurgery) return false;
        if (h.clearanceConfirmed()) return false;
        if (h.hasFlag("cirugia_sin_aval")) return true;
        // Ante respuestas contradictorias se elige lo más prudente.
        if (reasonPostOp && "no".equals(profile.string("h_postop_clearance"))) return true;
        return reasonPostOp && !screenedSurgery && !profile.yes("h_postop_clearance");
    }

    @Override
    public EnrollmentResult enroll(UserContext user, Answers a) {
        HealthSummary h = user.health();
        int age = user.profile().ageOr(40);
        boolean falls = a.yes("h_falls");
        String reason = a.string("h_reason", "postura");
        int painRest = clamp(a.integer("h_pain_rest", 0), 0, 10);
        int painActivity = clamp(a.integer("h_pain_activity", painRest), 0, 10);
        String track = HealthTriage.chooseTrack(a.string("h_zone"), h.injuredZones(), age, falls, h.activeTags(), reason);
        boolean locked = postOpLocked(h, a);
        int phase = HealthTriage.initialPhase(track, reason, a.string("h_onset"), painRest, painActivity, locked,
                h.mustRefer(), h.hasFlag("inactivo"), falls, age, h.worstInjurySeverity());
        TrackInfo info = kit.catalog().track(KEY, track).orElse(null);
        String trackName = info == null ? track : info.name();
        ProgressionInfo prog = kit.catalog().progression(KEY, progKey(track)).orElse(null);
        String phaseName = prog == null ? "Fase " + (phase + 1) : prog.step(phase).name();

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("phase", phase);
        state.put("greenStreak", 0);
        state.put("redStreak", 0);
        state.put("greenRun", 0);
        state.put("volumeFactor", Math.max(painRest, painActivity) >= 7 ? 0.8 : 1.0);
        state.put("fallRisk", falls);
        state.put("baselinePain", painRest);

        List<Factor> why = new ArrayList<>();
        why.add(Factor.of("ruta", 0, "Ruta " + trackName + ": " + trackReason(track, a, h, age, falls)));
        why.add(Factor.of("fase", 0, "Empiezas en " + phaseName + " " + phaseReason(phase, reason, painRest, painActivity, a.string("h_onset"), locked, h)));
        why.add(Factor.of("modelo", 0, "Avanzas de fase con sesiones seguidas en verde: dolor ≤ 3/10 durante y a las 24 h (basado en Silbernagel et al., 2007)"));

        List<GoalSpec> goals = goals(track, phase, painActivity, a);
        List<String> notices = new ArrayList<>();
        notices.add(DISCLAIMER);
        if (h.mustRefer()) {
            notices.add("Marcaste señales que un profesional debe revisar. Consulta antes de progresar: mientras tanto solo verás una sesión suave de respiración y movilidad.");
        }
        if (locked) {
            notices.add("Como te operaron y aún no confirmas la autorización de tu profesional, el plan se queda en movilidad suave de la fase 1. Cuando la tengas, confírmala en tu perfil de salud y desbloquearemos las siguientes fases.");
        }
        if (Math.max(painRest, painActivity) >= 7) {
            notices.add("Tu dolor es alto (" + Math.max(painRest, painActivity) + "/10): empezamos con muy poco volumen. Si no mejora en 1–2 semanas o empeora, consulta a un profesional.");
        }
        if ("lesion_reciente".equals(reason) && !a.yes("h_guided")) {
            notices.add("Si la lesión fue por un golpe o una torcedura fuerte, o no puedes apoyar el peso, consulta antes de empezar.");
        }
        if (a.yes("h_guided")) {
            notices.add("Si tu profesional te indica algo distinto a este plan, sigue sus indicaciones.");
        }
        if (h.injuredZones().stream().anyMatch(z -> z.equals("codo") || z.equals("muneca"))) {
            notices.add("Aún no tenemos un protocolo específico para codo o muñeca; evitamos los apoyos sobre las manos que te molesten.");
        }
        Map<String, Object> profile = a.asMap();
        profile.put("h_track_chosen", track);
        return new EnrollmentResult(track, phase + 1, profile, state, goals, Map.of(progKey(track), phase), why, notices);
    }

    private static String trackReason(String track, Answers a, HealthSummary h, int age, boolean falls) {
        if (HealthTriage.ZONE_TRACKS.contains(track)) return "trabajaremos la zona que indicaste";
        return switch (track) {
            case "adulto_mayor" -> falls ? "nos contaste caídas o inestabilidad; priorizamos fuerza y equilibrio (CDC STEADI)"
                    : "a partir de los 65 años la fuerza y el equilibrio previenen caídas (CDC STEADI, OMS 2020)";
            case "cardiometabolico" -> "tu chequeo indica una condición cardiometabólica; la actividad progresiva es parte de su cuidado (OMS 2020)";
            default -> "pausas activas y movilidad para contrarrestar el sedentarismo (OMS 2020)";
        };
    }

    private static String phaseReason(int phase, String reason, int rest, int act, String onset, boolean locked,
            HealthSummary h) {
        if (h.mustRefer()) return "porque primero debe valorarte un profesional";
        if (locked) return "hasta que confirmes la autorización tras tu cirugía";
        if ("postoperatorio".equals(reason)) return "como regreso gradual tras la cirugía";
        if (Math.max(rest, act) >= 4) return "porque tu dolor (" + Math.max(rest, act) + "/10) aún es moderado o alto";
        if ("menos_6s".equals(onset) || "lesion_reciente".equals(reason)) return "porque la molestia es reciente";
        return phase == 0 ? "para crear la base con seguridad" : "porque tu dolor es bajo y la molestia no es reciente";
    }

    private List<GoalSpec> goals(String track, int phase, int painActivity, Answers a) {
        List<GoalSpec> g = new ArrayList<>();
        LocalDate now = LocalDate.now(MetricRepository.ZONE);
        if (painActivity > 3) {
            g.add(new GoalSpec("hea_dolor_3", "Bajar tu dolor con actividad a 3/10 o menos",
                    Map.of("metric", "hea_dolor_despues", "max", 3), now.plusWeeks(6)));
        }
        switch (track) {
            case "adulto_mayor" -> {
                g.add(new GoalSpec("hea_silla_12", "Levantarte de la silla 12 veces en 30 s",
                        Map.of("metric", "hea_chair_stand", "min", 12), now.plusWeeks(8)));
                g.add(new GoalSpec("hea_tug_12", "Hacer el Timed Up and Go en menos de 12 segundos",
                        Map.of("metric", "hea_tug_s", "max", 12), now.plusWeeks(8)));
                g.add(new GoalSpec("hea_tandem", "Sostener 10 s la posición en tándem (etapa 3 de equilibrio)",
                        Map.of("metric", "hea_equilibrio_etapa", "min", 3), now.plusWeeks(6)));
            }
            case "cardiometabolico" -> {
                g.add(new GoalSpec("hea_oms_150", "Sumar 150 minutos semanales de actividad moderada (OMS)",
                        Map.of("metric", "minutos_sesion", "weeklySum", 150), now.plusWeeks(12)));
                g.add(new GoalSpec("hea_fuerza_2", "Hacer ejercicios de fuerza 2 días por semana",
                        Map.of("strengthDaysPerWeek", 2), now.plusWeeks(4)));
            }
            case "movilidad_general" -> g.add(new GoalSpec("hea_pausas", "Moverte al menos 3 días por semana durante 6 semanas",
                    Map.of("sessionsPerWeek", 3, "weeks", 6), now.plusWeeks(6)));
            default -> {
                g.add(phase == 0
                        ? new GoalSpec("hea_fase_1", "Completar la fase 1 con el dolor bajo control",
                                Map.of("progression", progKey(track), "step", 1), now.plusWeeks(3))
                        : new GoalSpec("hea_fase_3", "Llegar a la fase de función y retorno a la actividad",
                                Map.of("progression", progKey(track), "step", 2), now.plusWeeks(5)));
                if (a.contains("h_limits", "silla") || "rodilla".equals(track) || "cadera".equals(track)) {
                    g.add(new GoalSpec("hea_silla_12", "Levantarte de la silla 12 veces en 30 s",
                            Map.of("metric", "hea_chair_stand", "min", 12), now.plusWeeks(8)));
                }
            }
        }
        return g;
    }

    // ───────────────────────── Plan ─────────────────────────

    static String progKey(String track) {
        return "hea_" + track;
    }

    private String track(WorldContext ctx) {
        return ctx.world().trackKey() == null ? "movilidad_general" : ctx.world().trackKey();
    }

    private ProgressionInfo progression(WorldContext ctx) {
        return kit.catalog().progression(KEY, progKey(track(ctx))).orElse(null);
    }

    static int phaseOf(WorldContext ctx, ProgressionInfo prog) {
        Double p = ctx.world().stateNumber("phase");
        int last = prog == null ? 2 : prog.lastIndex();
        return clamp(p == null ? 0 : p.intValue(), 0, last);
    }

    static int sessionsPerWeek(UserContext user) {
        return user.health().mustRefer() ? 1 : clamp(user.profile().daysOr(3), 3, 5);
    }

    static int minutesTarget(UserContext user, String track) {
        int cap = "cardiometabolico".equals(track) ? 45 : "movilidad_general".equals(track) ? 20 : 30;
        return clamp(user.profile().minutesOr(20), 15, cap);
    }

    @Override
    public GeneratedPlan generatePlan(WorldContext ctx) {
        UserContext user = ctx.user();
        String track = track(ctx);
        TrackInfo info = kit.catalog().track(KEY, track).orElse(null);
        String trackName = info == null ? track : info.name();
        ProgressionInfo prog = progression(ctx);
        boolean locked = postOpLocked(user.health(), ctx.world().profile());
        int phase = locked ? 0 : phaseOf(ctx, prog);
        String phaseName = prog == null ? "Fase " + (phase + 1) : prog.step(phase).name();
        List<WorldMovement> usable = kit.usableMovements(KEY, user);
        if (user.health().mustRefer()) return minimalPlan(ctx, usable, trackName);

        Prescription target = prog == null ? null : prog.step(phase).target();
        double vf = volumeFactor(ctx);
        int days = sessionsPerWeek(user);
        int budgetS = minutesTarget(user, track) * 60;
        boolean cardio = "cardiometabolico".equals(track);
        boolean older = "adulto_mayor".equals(track);
        boolean fallRisk = Boolean.TRUE.equals(ctx.world().stateValue("fallRisk"));
        Set<String> liked = kit.liked(user.userId(), KEY);
        Set<String> disliked = kit.disliked(user.userId(), KEY);
        Random rnd = WorldToolkit.stableRandom(user.userId(), KEY + track + phase);

        List<WorldMovement> mine = usable.stream().filter(m -> inTrack(m, track)).toList();
        List<WorldMovement> warmups = mine.stream().filter(m -> "calentamiento".equals(m.role()))
                .sorted(java.util.Comparator.comparing((WorldMovement m) -> !"respiracion".equals(m.pattern()))).toList();
        List<WorldMovement> cooldowns = mine.stream().filter(m -> "vuelta_calma".equals(m.role())).toList();
        List<WorldMovement> mains = mine.stream().filter(m -> "principal".equals(m.role()) && inPhase(m, track, phase + 1)).toList();
        List<Integer> weekdays = kit.weekdays(user.profile(), days);
        Map<String, Integer> uses = new LinkedHashMap<>();
        Set<Integer> strengthDays = cardio ? Set.of(0, (days + 1) / 2) : Set.of();
        List<GeneratedSession> sessions = new ArrayList<>();

        for (int d = 0; d < days; d++) {
            List<GeneratedBlock> blocks = new ArrayList<>();
            for (WorldMovement w : warmups) {
                blocks.add(GeneratedBlock.of("calentamiento", w.key(), w.name(), movementRx(w, null, phase, 1.0)));
            }
            WorldMovement cool = cooldowns.isEmpty() ? null : cooldowns.get((d + phase) % cooldowns.size());
            GeneratedBlock coolBlock = cool == null ? null
                    : GeneratedBlock.of("vuelta_calma", cool.key(), cool.name(), movementRx(cool, null, phase, 1.0));
            int used = blocks.stream().mapToInt(b -> seconds(b.prescription())).sum()
                    + (coolBlock == null ? 0 : seconds(coolBlock.prescription()));
            Set<String> today = new HashSet<>();
            Map<String, Integer> patternsToday = new LinkedHashMap<>();
            List<GeneratedBlock> main = new ArrayList<>();

            if (cardio) {
                List<WorldMovement> aerobic = mains.stream().filter(m -> "aerobico".equals(m.pattern())).toList();
                WorldMovement a = pick(aerobic, today, uses, liked, disliked, Map.of(), track, phase, rnd, null);
                if (a != null) {
                    int dur = target != null && target.durationS() != null ? target.durationS() : 600;
                    dur = Math.max(300, (int) Math.round(dur * vf / 60.0) * 60);
                    String intensity = target != null && target.intensity() != null ? target.intensity() : "moderada";
                    Prescription p = Prescription.time(dur, intensity).withPainMax(3)
                            .withCue("Prueba del habla: en moderada puedes hablar, pero no cantar");
                    main.add(GeneratedBlock.of("principal", a.key(), a.name(), p).withProgression(progKey(track), phase));
                    used += seconds(p);
                    note(a, today, uses, patternsToday);
                }
                if (strengthDays.contains(d)) {
                    Prescription strength = Prescription.reps(phase == 0 ? 1 : 2, 8, 12, 45).withTempo("2-1-2")
                            .withPainMax(3).withIntensity("suave");
                    List<WorldMovement> pool = mains.stream().filter(m -> !"aerobico".equals(m.pattern())).toList();
                    for (int i = 0; i < 3; i++) {
                        WorldMovement m = pick(pool, today, uses, liked, disliked, patternsToday, track, phase, rnd, null);
                        if (m == null) break;
                        Prescription p = movementRx(m, strength, phase, vf);
                        main.add(GeneratedBlock.of("accesorio", m.key(), m.name(), p));
                        used += seconds(p);
                        note(m, today, uses, patternsToday);
                    }
                }
            } else {
                List<WorldMovement> pool = mains.stream().filter(m -> !"aerobico".equals(m.pattern())).toList();
                int balanceSlots = older ? (fallRisk ? 2 : 1) : 0;
                for (int i = 0; i < balanceSlots; i++) {
                    WorldMovement m = pick(pool, today, uses, liked, disliked, patternsToday, track, phase, rnd, "equilibrio");
                    if (m == null) break;
                    Prescription p = movementRx(m, target, phase, vf);
                    main.add(GeneratedBlock.of("principal", m.key(), m.name(), p).withProgression(progKey(track), phase));
                    used += seconds(p);
                    note(m, today, uses, patternsToday);
                }
                while (main.size() < 5) {
                    WorldMovement m = pick(pool, today, uses, liked, disliked, patternsToday, track, phase, rnd, null);
                    if (m == null) break;
                    Prescription p = movementRx(m, target, phase, vf);
                    if (main.size() >= 3 && used + seconds(p) > budgetS) break;
                    main.add(GeneratedBlock.of("principal", m.key(), m.name(), p).withProgression(progKey(track), phase));
                    used += seconds(p);
                    note(m, today, uses, patternsToday);
                }
                // Caminata corta al final cuando la ruta la incluye y queda tiempo.
                List<WorldMovement> walks = mains.stream().filter(m -> "aerobico".equals(m.pattern())).toList();
                if (!walks.isEmpty() && (d % 2 == 0 || "lumbar".equals(track))) {
                    WorldMovement w = walks.get(d % walks.size());
                    int left = budgetS - used;
                    if (left >= 300) {
                        int dur = Math.min(left, (int) Math.round(Math.min(900, 300 + phase * 300) * vf / 60.0) * 60);
                        Prescription p = Prescription.time(Math.max(300, dur / 60 * 60), phase == 0 ? "suave" : "suave a moderada")
                                .withPainMax(3).withCue("Paso cómodo; acorta si el dolor pasa de 3/10");
                        main.add(GeneratedBlock.of("principal", w.key(), w.name(), p));
                        used += seconds(p);
                    }
                }
            }
            blocks.addAll(main.stream().map(b -> b.withNotes(blockNote(b))).toList());
            if (coolBlock != null) blocks.add(coolBlock);
            String title = "Sesión " + LETTERS[d % LETTERS.length] + " · Fase " + (phase + 1);
            String focus = cardio ? (strengthDays.contains(d) ? "Aeróbico + fuerza" : "Aeróbico")
                    : shortPhase(phaseName);
            String notes = SESSION_NOTE + (vf < 1 ? " Volumen al " + Math.round(vf * 100) + " % por tu último semáforo." : "");
            sessions.add(new GeneratedSession(0, d, d < weekdays.size() ? weekdays.get(d) : null, title, focus,
                    "protocolo", Math.max(10, Math.round(totalSeconds(blocks) / 60f)), notes, blocks));
        }

        List<Factor> why = new ArrayList<>();
        why.add(Factor.of("fase", 0, phaseName + (locked ? " (fija hasta que confirmes el aval de tu profesional tras la cirugía)" : "")));
        StepInfo step = prog == null ? null : prog.step(phase);
        Criteria c = Criteria.from(step == null ? null : step.unlockCriteria());
        if (c.canAdvance() && prog != null && phase < prog.lastIndex()) {
            why.add(Factor.of("avance", 0, "Pasas a la siguiente fase con " + c.sessions()
                    + " sesiones seguidas en verde (dolor ≤ 3/10 durante, después y a las 24 h)"));
        }
        why.add(Factor.of("dolor", 0, "Semáforo de dolor basado en el modelo de Silbernagel et al. (2007), con umbrales más conservadores"));
        why.add(Factor.of("fuente", 0, switch (track) {
            case "adulto_mayor" -> "Fuerza y equilibrio basados en el programa Otago y CDC STEADI (prevención de caídas)";
            case "cardiometabolico" -> "OMS 2020: 150–300 min semanales de actividad moderada y 2 días de fuerza; progresión gradual";
            case "movilidad_general" -> "OMS 2020: reducir el tiempo sentado y moverse más; pausas activas";
            default -> "Ejercicios terapéuticos generales basados en la OMS (Package of Interventions for Rehabilitation) y el NIA";
        }));
        if (vf < 1) why.add(Factor.of("volumen", 0, "Volumen al " + Math.round(vf * 100) + " % por tu semáforo reciente"));
        why.add(Factor.of("sesiones", 0, days + " sesiones cortas por semana (≈" + minutesTarget(user, track) + " min), de bajo impacto"));
        int excluded = kit.catalog().movements(KEY).size() - usable.size();
        if (excluded > 0) why.add(Factor.of("seguridad", 0, excluded + " ejercicios descartados por tu equipamiento o tu salud"));
        return new GeneratedPlan(trackName + " · " + phaseName,
                "Semana tipo que se repite. Avanzas o retrocedes de fase según tu semáforo de dolor. " + DISCLAIMER,
                1, null, why, sessions);
    }

    /** Con banderas rojas: una única sesión de respiración y movilidad muy suave, y derivación. */
    private GeneratedPlan minimalPlan(WorldContext ctx, List<WorldMovement> usable, String trackName) {
        List<GeneratedBlock> blocks = new ArrayList<>();
        for (String key : List.of("hea_respiracion_diafragmatica", "hea_movilidad_articular_suave", "hea_relajacion_guiada")) {
            usable.stream().filter(m -> m.key().equals(key)).findFirst().ifPresent(m -> blocks.add(GeneratedBlock.of(
                    "vuelta_calma".equals(m.role()) ? "vuelta_calma" : "calentamiento", m.key(), m.name(),
                    Prescription.time(180, "muy suave").withPainMax(2)
                            .withCue("Sin dolor: si algo molesta, detente"))));
        }
        List<Integer> weekdays = kit.weekdays(ctx.user().profile(), 1);
        GeneratedSession s = new GeneratedSession(0, 0, weekdays.isEmpty() ? null : weekdays.get(0),
                "Sesión suave mientras consultas", "Respiración y movilidad", "protocolo",
                Math.max(5, Math.round(totalSeconds(blocks) / 60f)),
                "Consulta a un profesional de la salud antes de progresar. Esta sesión solo mantiene una movilidad muy suave; "
                        + "si los síntomas empeoran, busca atención médica.", blocks);
        List<Factor> why = List.of(
                Factor.of("seguridad", 0, "Marcaste señales de alarma (banderas rojas o síntomas cardiovasculares sin aval): detenemos la progresión hasta que te valore un profesional (Finucane et al., 2020; ACSM 2015)"),
                Factor.of("contenido", 0, "Solo respiración y movilidad muy suave, sin carga"));
        return new GeneratedPlan(trackName + " · En pausa por seguridad",
                "Plan mínimo hasta que un profesional te valore. " + DISCLAIMER, 1, null, why, List.of(s));
    }

    private static String shortPhase(String phaseName) {
        int i = phaseName.indexOf('·');
        return i < 0 ? phaseName : phaseName.substring(i + 1).trim();
    }

    private static String blockNote(GeneratedBlock b) {
        Prescription p = b.prescription();
        if (p != null && "cada_lado".equals(p.side())) return "Haz las series con un lado y luego con el otro. Dolor ≤ 3/10.";
        return "Dolor ≤ 3/10. Muévete en el rango cómodo.";
    }

    static double volumeFactor(WorldContext ctx) {
        Double v = ctx.world().stateNumber("volumeFactor");
        return v == null ? 1.0 : Math.max(PainMonitoring.VOLUME_MIN, Math.min(1.0, v));
    }

    @SuppressWarnings("unchecked")
    static List<Integer> phasesFor(WorldMovement m, String track) {
        Object by = m.attr("phasesByTrack");
        List<Integer> out = new ArrayList<>();
        if (by instanceof Map<?, ?> map && map.get(track) instanceof List<?> l) {
            for (Object o : l) if (o instanceof Number n) out.add(n.intValue());
            return out;
        }
        // Formato corto alternativo: {"tracks":[...], "phase": n}
        Object tracks = m.attr("tracks");
        if (tracks instanceof List<?> tl && tl.contains(track)) {
            int p = (int) m.attrNumber("phase", 1);
            for (int i = p; i <= 3; i++) out.add(i);
        }
        return out;
    }

    static boolean inTrack(WorldMovement m, String track) {
        return !phasesFor(m, track).isEmpty();
    }

    static boolean inPhase(WorldMovement m, String track, int phase1) {
        return phasesFor(m, track).contains(phase1);
    }

    private static void note(WorldMovement m, Set<String> today, Map<String, Integer> uses, Map<String, Integer> patterns) {
        today.add(m.key());
        uses.merge(m.key(), 1, Integer::sum);
        patterns.merge(m.pattern() == null ? "" : m.pattern(), 1, Integer::sum);
    }

    private static WorldMovement pick(List<WorldMovement> pool, Set<String> today, Map<String, Integer> uses,
            Set<String> liked, Set<String> disliked, Map<String, Integer> patternsToday, String track, int phase,
            Random rnd, String requiredPattern) {
        List<WorldMovement> candidates = requiredPattern == null ? pool
                : pool.stream().filter(m -> requiredPattern.equals(m.pattern())).toList();
        return WorldToolkit.pick(candidates, today, m -> {
            double s = 10;
            List<Integer> ph = phasesFor(m, track);
            if (!ph.isEmpty() && ph.get(0) == phase + 1) s += 2; // ejercicio propio de la fase
            if (liked.contains(m.key())) s += 3;
            if (disliked.contains(m.key())) s -= 6;
            s -= uses.getOrDefault(m.key(), 0) * 2.5;
            s -= patternsToday.getOrDefault(m.pattern() == null ? "" : m.pattern(), 0) * 1.5;
            return s;
        }, rnd);
    }

    /** Prescripción de un movimiento: su forma propia (attrs.rx) con la dosis de la fase y el factor de volumen. */
    @SuppressWarnings("unchecked")
    static Prescription movementRx(WorldMovement m, Prescription target, int phase, double vf) {
        Map<String, Object> rx = m.attr("rx") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        String type = rx.get("type") == null ? "reps" : String.valueOf(rx.get("type"));
        String side = m.attrTrue("unilateral") ? "cada_lado" : null;
        boolean reps = target != null && "reps".equals(target.type());
        int sets = reps && target.sets() != null ? target.sets() : 2;
        int rest = target != null && target.restS() != null ? target.restS() : 45;
        int painMax = target != null && target.painMax() != null ? target.painMax() : 3;
        String intensity = rx.get("intensity") != null ? String.valueOf(rx.get("intensity"))
                : target != null && target.intensity() != null ? target.intensity() : "suave";
        String tempo = rx.get("tempo") != null ? String.valueOf(rx.get("tempo"))
                : target != null && target.tempo() != null ? target.tempo() : "3-1-3";
        String cue = m.movement().cues().isEmpty() ? null : m.movement().cues().get(0);
        Integer outSets = null, outReps = null, repsMin = null, repsMax = null, holdS = null, durationS = null;
        switch (type) {
            case "hold" -> {
                outSets = clamp(sets, 1, 3);
                holdS = Math.max(5, (int) Math.round((num(rx.get("holdS"), 10) + 5 * phase) * vf));
            }
            case "time" -> {
                durationS = Math.max(60, (int) Math.round(num(rx.get("durationS"), 120) * vf / 30.0) * 30);
                tempo = null;
                outSets = null;
                rest = 0;
            }
            default -> {
                if (Boolean.TRUE.equals(rx.get("fixed"))) {
                    outSets = (int) num(rx.get("sets"), 2);
                    int r = (int) num(rx.get("reps"), 10);
                    outReps = r > 3 ? Math.max(3, (int) Math.round(r * vf)) : r;
                    if (rx.get("tempo") == null) tempo = null; // pasos o alfabetos: sin tempo por repetición
                } else {
                    int lo = reps && target.repsMin() != null ? target.repsMin() : 8;
                    int hi = reps && target.repsMax() != null ? target.repsMax() : 12;
                    outSets = sets;
                    repsMin = Math.max(3, (int) Math.round(lo * vf));
                    repsMax = Math.max(repsMin, (int) Math.round(hi * vf));
                }
            }
        }
        if (outSets != null && vf <= PainMonitoring.VOLUME_MIN + 1e-9 && outSets > 1) outSets--;
        return new Prescription(type.equals("hold") || type.equals("time") ? type : "reps", outSets, outReps,
                repsMin, repsMax, null, null, holdS, durationS, null, null, null, null,
                outSets == null || outSets <= 1 ? null : rest, null, intensity, tempo, painMax, side, cue);
    }

    /** Duración estimada de un bloque, teniendo en cuenta el tempo lento y los ejercicios por lado. */
    static int seconds(Prescription p) {
        if (p == null) return 60;
        int sides = "cada_lado".equals(p.side()) ? 2 : 1;
        int sets = p.sets() == null ? 1 : p.sets();
        int rest = p.restS() == null ? 0 : p.restS();
        int body = switch (p.type()) {
            case "reps" -> {
                int r = p.reps() != null ? p.reps() : p.repsMax() != null ? p.repsMax() : 10;
                yield sets * r * tempoSeconds(p.tempo()) * sides + Math.max(0, sets - 1) * rest;
            }
            case "hold" -> sets * (p.holdS() == null ? 10 : p.holdS()) * sides + Math.max(0, sets - 1) * rest;
            case "time" -> p.durationS() == null ? 120 : p.durationS();
            default -> p.estimatedSeconds();
        };
        return body + 20;
    }

    static int tempoSeconds(String tempo) {
        if (tempo == null) return 4;
        java.util.regex.Matcher hold = java.util.regex.Pattern.compile("sost[eé]n\\s*(\\d+)").matcher(tempo);
        if (hold.find()) return Integer.parseInt(hold.group(1)) + 2;
        int sum = 0;
        for (String part : tempo.split("-")) {
            try {
                sum += Integer.parseInt(part.trim());
            } catch (NumberFormatException e) {
                return 4;
            }
        }
        return sum == 0 ? 4 : sum;
    }

    private static int totalSeconds(List<GeneratedBlock> blocks) {
        return blocks.stream().mapToInt(b -> seconds(b.prescription())).sum();
    }

    // ───────────────────────── Evaluación ─────────────────────────

    @Override
    public SessionOutcome evaluateSession(WorldContext ctx, LoggedSession session) {
        SessionOutcome.Builder out = SessionOutcome.builder();
        UserContext user = ctx.user();
        String track = track(ctx);
        ProgressionInfo prog = progression(ctx);
        int last = prog == null ? 2 : prog.lastIndex();
        int phase = phaseOf(ctx, prog);
        StepInfo step = prog == null ? null : prog.step(phase);
        Map<String, Object> unlock = step == null ? Map.of() : step.unlockCriteria();
        Criteria criteria = Criteria.from(unlock);

        Integer maxBlock = session.blocks().stream().map(LoggedBlock::pain).filter(p -> p != null)
                .max(Integer::compare).orElse(null);
        SessionSummary previous = sessionLogs.recent(ctx.world().id(), 5).stream()
                .filter(s -> !s.id().equals(session.id())).findFirst().orElse(null);
        // Si el dolor de 24 h de la sesión anterior ya se aplicó en onNextDayPain, no se vuelve a evaluar.
        boolean prevHandled = previous != null && handledNextDay(ctx).contains(previous.id().toString());
        PainMonitoring.Input input = new PainMonitoring.Input(session.painBefore(), session.painAfter(), maxBlock,
                previous == null ? null : previous.painBefore(),
                previous == null || prevHandled ? null : previous.painNextDay(), session.rpe());

        boolean locked = postOpLocked(user.health(), ctx.world().profile());
        int maxPhase = locked ? 0 : user.health().mustRefer() ? phase : last;
        String testMetric = unlock.get("testMetric") == null ? null : String.valueOf(unlock.get("testMetric"));
        boolean gateOk = true;
        if (testMetric != null) {
            double min = num(unlock.get("testMin"), 0);
            gateOk = metrics.latest(ctx.world().id(), testMetric).map(v -> v >= min).orElse(false);
        }
        PainMonitoring.State before = new PainMonitoring.State(phase, intState(ctx, "greenStreak"),
                intState(ctx, "redStreak"), volumeFactor(ctx));
        Decision d = PainMonitoring.decide(input, before, criteria, last, maxPhase, gateOk);

        // Métricas del nicho.
        if (session.painBefore() != null) out.metric("hea_dolor_antes", session.painBefore());
        if (maxBlock != null) out.metric("hea_dolor_durante", maxBlock);
        Integer after = session.painAfter() != null ? session.painAfter() : input.during();
        if (after != null) out.metric("hea_dolor_despues", after);
        if (d.light() != Light.SIN_DATOS) out.metric("hea_semaforo", d.light().code);
        LocalDate monday = session.performedOn().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int done = sessionLogs.countSince(ctx.world().id(), monday);
        out.metric("hea_adherencia", Math.min(100, Math.round(done * 100.0 / sessionsPerWeek(user))));

        // Estado.
        int greenRun = d.counted() ? intState(ctx, "greenRun") + 1 : d.light() == Light.VERDE || d.light() == Light.SIN_DATOS
                ? intState(ctx, "greenRun") : 0;
        out.state("phase", d.state().phase()).state("greenStreak", d.state().greenStreak())
                .state("redStreak", d.state().redStreak()).state("volumeFactor", d.state().volumeFactor())
                .state("greenRun", greenRun).state("referAdvised", d.referAdvised()
                        || (Boolean.TRUE.equals(ctx.world().stateValue("referAdvised")) && d.light() != Light.VERDE));
        if (d.light() != Light.SIN_DATOS) {
            out.state("lastLight", d.light().key()).state("lastLightOn", session.performedOn().toString());
        }
        if (d.state().phase() != phase) {
            out.step(progKey(track), d.state().phase());
            out.level(d.state().phase() + 1);
        }
        if (d.regressed() || d.volumeChanged(before)) out.regeneratePlan();

        // Mensajes.
        boolean byNextDay = d.nextDayLight().code > d.duringLight().code;
        String painText = byNextDay ? " por tu dolor a las 24 h (" + input.prevNextDay() + "/10)"
                : input.during() == null ? "" : " (dolor máximo " + input.during() + "/10)";
        String nextName = prog == null ? "la siguiente fase" : prog.step(Math.min(last, phase + 1)).name();
        switch (d.light()) {
            case VERDE -> {
                out.highlight("🟢 Semáforo verde" + painText + ". ¡Bien dosificado!");
                if (user.health().mustRefer()) {
                    out.highlight("Sesión suave registrada. Recuerda consultar a un profesional antes de progresar.");
                } else if (d.advanced()) {
                    out.highlight("¡Pasas a " + nextName + "! Empezamos con el 80 % del volumen para que el cuerpo se adapte.");
                    if (phase == 0) out.achievement("hea_primera_fase", "Primera fase completada", "🎉");
                    if (d.state().phase() == last) out.achievement("hea_fase_funcion", "Llegaste a la fase de función", "🏁");
                } else if (d.advanceBlocked()) {
                    if (locked) out.highlight("Cumples los criterios de avance, pero seguimos en la fase 1 hasta que confirmes la autorización de tu profesional tras la cirugía.");
                    else if (user.health().mustRefer()) out.highlight("Para avanzar de fase primero debe valorarte un profesional de la salud.");
                    else if (!gateOk) out.highlight("Cumples las sesiones en verde. Para pasar de fase, haz el test: "
                            + unlock.getOrDefault("testLabel", "test de la fase") + ".");
                } else if (!d.counted()) {
                    out.highlight("El esfuerzo fue alto (RPE " + session.rpe() + "): esta sesión no suma para avanzar. La próxima, baja un poco el ritmo.");
                } else if (criteria.canAdvance() && phase < last) {
                    out.highlight("Llevas " + d.state().greenStreak() + " de " + criteria.sessions() + " sesiones seguidas en verde para pasar a " + nextName + ".");
                }
            }
            case AMARILLO -> {
                out.highlight("🟡 Semáforo amarillo" + painText + ": mantenemos la fase y bajamos el volumen al "
                        + Math.round(d.state().volumeFactor() * 100) + " %.");
                if (d.nextDayLight() == Light.AMARILLO) out.highlight("Tras tu sesión anterior, el dolor a las 24 h no volvió a tu nivel habitual.");
                out.highlight("Reduce el rango o el ritmo en los ejercicios que molestan y mantén el dolor en 3/10 o menos.");
            }
            case ROJO -> {
                out.highlight("🔴 Semáforo rojo" + painText + (d.regressed()
                        ? ": volvemos a " + (prog == null ? "la fase anterior" : prog.step(d.state().phase()).name()) + " y bajamos el volumen."
                        : ": seguimos en la fase 1 con menos volumen."));
                if (d.nextDayLight() == Light.ROJO) out.highlight("El dolor a las 24 h de tu sesión anterior subió más de 3 puntos sobre tu nivel de base.");
                if (d.referAdvised()) out.highlight("Es la segunda sesión seguida en rojo: te recomendamos consultar a un profesional de la salud antes de continuar.");
                else out.highlight("Si el dolor no baja en 24–48 h, consulta a un profesional.");
            }
            case SIN_DATOS -> out.highlight("Registra tu dolor antes y después de la sesión: así ajustamos tu protocolo con seguridad.");
        }
        if (greenRun >= 6) out.achievement("hea_dos_semanas_verde", "2 semanas con el dolor bajo control", "🟢");
        return out.build();
    }

    private static int intState(WorldContext ctx, String key) {
        Double v = ctx.world().stateNumber(key);
        return v == null ? 0 : v.intValue();
    }

    // ───────────────────────── Tests ─────────────────────────

    @Override
    public AssessmentOutcome onAssessment(WorldContext ctx, String key, double value) {
        AssessmentInfo info = kit.catalog().assessment(key).orElse(null);
        if (info == null) return AssessmentOutcome.none();
        List<String> highlights = new ArrayList<>();
        SessionOutcome.Builder effects = SessionOutcome.builder();
        String track = track(ctx);
        if (FALL_TESTS.contains(key)) {
            Map<String, Object> band = HealthTriage.matchBand(info.norms(), value, ctx.user().profile().ageOr(-1),
                    ctx.user().profile().sex());
            boolean risk = HealthTriage.isRisk(band);
            Map<String, Object> tests = new LinkedHashMap<>(ctx.world().stateMap("fallTests"));
            tests.put(key, risk);
            boolean anyRisk = tests.values().stream().anyMatch(Boolean.TRUE::equals) || a(ctx).yes("h_falls");
            boolean before = Boolean.TRUE.equals(ctx.world().stateValue("fallRisk"));
            effects.state("fallTests", tests).state("fallRisk", anyRisk);
            if (risk) {
                highlights.add("Según CDC STEADI, este resultado indica un riesgo aumentado de caídas. Coméntalo con tu médico: puede valorar tu equilibrio, tus medicamentos y tu visión.");
                if ("adulto_mayor".equals(track)) {
                    highlights.add("Añadimos más ejercicios de equilibrio con apoyo a tu plan.");
                } else {
                    highlights.add("Te sugerimos también la ruta Adulto mayor (fuerza y equilibrio) de este mundo.");
                }
            } else if (band == null || (!band.containsKey("min") && !band.containsKey("max"))) {
                highlights.add("Registrado como valor de seguimiento: los puntos de corte de CDC STEADI aplican de 60 a 94 años.");
            } else {
                highlights.add("Sin riesgo aumentado de caídas en este test. ¡Sigue así!");
            }
            if (anyRisk != before && "adulto_mayor".equals(track)) effects.regeneratePlan();
            if ("hea_test_equilibrio_4".equals(key) && value >= 3 && "adulto_mayor".equals(track)) {
                highlights.add("Sostienes el tándem 10 s: cumples el criterio de equilibrio para la fase sin apoyo.");
            }
        } else if ("hea_test_funcion".equals(key)) {
            highlights.add("Registrado. Repite este autorreporte cada 2 semanas con la misma actividad para ver tu evolución.");
        }
        return new AssessmentOutcome(null, highlights, effects.build());
    }

    private static Answers a(WorldContext ctx) {
        return ctx.world().profile() == null ? Answers.empty() : ctx.world().profile();
    }

    // ───────────────────────── Dashboard ─────────────────────────

    @Override
    public List<Widget> dashboard(WorldContext ctx, PlanView plan) {
        List<Widget> w = new ArrayList<>();
        ProgressionInfo prog = progression(ctx);
        w.add(Widget.notice("descargo", "info", "Programa educativo", DISCLAIMER
                + " Si aparece dolor intenso, hinchazón, fiebre, pérdida de fuerza o sensibilidad, detente y consulta."));
        if (postOpLocked(ctx.user().health(), a(ctx))) {
            w.add(Widget.notice("aval_cirugia", "warn", "Fase 1 hasta tu autorización",
                    "Tras tu cirugía, el plan se queda en movilidad suave. Cuando tu profesional te autorice, confírmalo en tu perfil de salud."));
        }
        if (Boolean.TRUE.equals(ctx.world().stateValue("referAdvised"))) {
            w.add(Widget.notice("consulta_rojo", "warn", "Consulta a un profesional",
                    "Tuviste dos sesiones seguidas en rojo. Bajamos de fase y de volumen; te recomendamos una valoración antes de seguir."));
        }
        w.add(trafficLight(ctx));
        w.add(Widget.insights("Para ti", insights(ctx, plan)));
        w.add(Widget.nextSession("Tu próxima sesión", plan.nextSession()));
        w.add(phaseTrack(ctx, prog));
        w.add(Widget.line("dolor_despues", "Tendencia de tu dolor", "Dolor al terminar cada sesión (meta ≤ 3/10)", "/10",
                metrics.series(ctx.world().id(), "hea_dolor_despues", 20), "down", 3.0));
        int planned = Math.max(1, plan.currentWeekSessions().size());
        double pct = Math.min(100, Math.round(plan.doneThisWeek() * 100.0 / planned));
        w.add(Widget.gauge("adherencia", "Adherencia de la semana", null, pct, 0, 100, "%", List.of(
                new Zone(0, 50, "bad", "Baja"), new Zone(50, 80, "warn", "En camino"), new Zone(80, 100, "good", "Constante")),
                plan.doneThisWeek() + " de " + planned + " sesiones"));
        return w;
    }

    private Widget trafficLight(WorldContext ctx) {
        Light light = PainMonitoring.Light.ofCode(metrics.latest(ctx.world().id(), "hea_semaforo").orElse(null));
        Double pain = metrics.latest(ctx.world().id(), "hea_dolor_despues").orElse(null);
        String msg = switch (light) {
            case VERDE -> "Tu última sesión estuvo en verde: el dolor se mantuvo en 3/10 o menos. Sigue así.";
            case AMARILLO -> "Tu última sesión estuvo en amarillo: mantenemos la fase con menos volumen. Muévete en el rango cómodo.";
            case ROJO -> "Tu última sesión estuvo en rojo: bajamos de fase y de volumen. Si el dolor no cede en 24–48 h, consulta.";
            case SIN_DATOS -> "Registra tu dolor antes y después de cada sesión para encender tu semáforo.";
        };
        return Widget.trafficLight("semaforo", "Tu semáforo de dolor", light.key(), pain, "/10", msg);
    }

    private List<Insight> insights(WorldContext ctx, PlanView plan) {
        List<Insight> out = new ArrayList<>();
        LocalDate today = LocalDate.now(MetricRepository.ZONE);
        sessionLogs.pendingNextDayCheck(ctx.world().id(), today).ifPresent(s -> out.add(new Insight("🌅",
                "¿Cómo amaneciste después de tu sesión?",
                "Cuéntanos tu dolor de 0 a 10 tras la sesión del " + s.performedOn()
                        + ". Si volvió a tu nivel habitual, vas bien; si subió, ajustamos.",
                "info", "Registrar", "/pain-check/" + s.id())));
        String last = ctx.world().stateString("lastLight");
        if ("amarillo".equals(last)) {
            out.add(Insight.of("🟡", "Baja un cambio", "Reduce el rango o el ritmo en los ejercicios que molestan; el volumen ya está ajustado.", "warn"));
        } else if ("rojo".equals(last)) {
            out.add(Insight.of("🔴", "Prioriza la calma", "Hoy haz solo lo que no aumente el dolor. Si en 24–48 h no mejora, consulta a un profesional.", "warn"));
        }
        if (Boolean.TRUE.equals(ctx.world().stateValue("fallRisk"))) {
            out.add(Insight.of("🧱", "Equilibrio con apoyo", "Haz los ejercicios de equilibrio siempre junto a una pared o mesón y, si puedes, con alguien cerca.", "warn"));
        }
        if (metrics.count(ctx.world().id(), "hea_chair_stand") == 0
                && ("adulto_mayor".equals(track(ctx)) || ctx.user().profile().ageOr(0) >= 60)) {
            out.add(new Insight("📏", "Haz tus tests de STEADI", "Levantarte de la silla, TUG y equilibrio: 5 minutos para medir tu punto de partida.",
                    "info", "Ver tests", "/assessments"));
        }
        if (plan.doneThisWeek() == 0) {
            out.add(new Insight("▶️", "Empieza tu semana", "Sesión corta y suave: registra tu dolor antes de empezar.", "info", "Empezar", "/session"));
        }
        if (out.isEmpty()) out.add(Insight.of("✅", "Vas bien", "Sigue registrando tu dolor: es lo que guía tu avance de fase.", "good"));
        return out;
    }

    private Widget phaseTrack(WorldContext ctx, ProgressionInfo prog) {
        List<Phase> phases = new ArrayList<>();
        if (prog != null) {
            int phase = phaseOf(ctx, prog);
            boolean locked = postOpLocked(ctx.user().health(), a(ctx));
            int green = intState(ctx, "greenStreak");
            for (StepInfo s : prog.steps()) {
                int i = s.index();
                Criteria prev = i == 0 ? null : Criteria.from(prog.step(i - 1).unlockCriteria());
                Criteria own = Criteria.from(s.unlockCriteria());
                String status = i < phase ? "done" : i == phase ? "current" : "locked";
                String detail;
                if (i < phase) detail = "Completada";
                else if (i == phase) detail = own.canAdvance() && i < prog.lastIndex()
                        ? green + " de " + own.sessions() + " sesiones en verde" + (locked ? " · requiere el aval de tu profesional" : "")
                        : "Mantén 2–3 sesiones por semana con dolor ≤ 3/10";
                else {
                    detail = prev != null && prev.canAdvance() ? "Se desbloquea con " + prev.sessions() + " sesiones seguidas en verde" : "Bloqueada";
                    Object label = prog.step(i - 1).unlockCriteria().get("testLabel");
                    if (label != null) detail += " y " + label;
                    if (locked) detail += " (y el aval de tu profesional)";
                }
                phases.add(new Phase(s.name(), status, detail));
            }
        }
        return Widget.phaseTrack("fases", "Tus fases", prog == null ? null : prog.name(), phases);
    }

    @Override
    public List<Widget> progress(WorldContext ctx) {
        List<Widget> w = new ArrayList<>();
        var uw = ctx.world().id();
        w.add(Widget.line("dolor_antes", "Dolor antes de la sesión", "En reposo", "/10", metrics.series(uw, "hea_dolor_antes", 30), "down", null));
        w.add(Widget.line("dolor_despues", "Dolor después de la sesión", "Meta ≤ 3/10", "/10", metrics.series(uw, "hea_dolor_despues", 30), "down", 3.0));
        w.add(Widget.line("dolor_24h", "Dolor a las 24 horas", "Debe volver a tu nivel habitual", "/10", metrics.series(uw, "dolor_24h", 30), "down", 3.0));
        w.add(phaseTrack(ctx, progression(ctx)));
        if (metrics.count(uw, "hea_chair_stand") > 0) {
            w.add(Widget.line("silla_30s", "Levantarte de la silla (30 s)", "CDC STEADI", "reps", metrics.series(uw, "hea_chair_stand", 20), "up", null));
        }
        if (metrics.count(uw, "hea_tug_s") > 0) {
            w.add(Widget.line("tug", "Timed Up and Go", "≥ 12 s = riesgo de caídas (CDC STEADI)", "s", metrics.series(uw, "hea_tug_s", 20), "down", 12.0));
        }
        if (metrics.count(uw, "hea_equilibrio_etapa") > 0) {
            w.add(Widget.line("equilibrio", "Equilibrio en 4 etapas", "Etapa 3 (tándem) = sin riesgo aumentado", "etapa",
                    metrics.series(uw, "hea_equilibrio_etapa", 20), "up", 3.0));
        }
        w.add(Widget.weekBars("sesiones", "Sesiones por semana", null, "sesiones",
                metrics.weekly(uw, "hea_adherencia", 8, "count"), (double) sessionsPerWeek(ctx.user())));
        if ("cardiometabolico".equals(track(ctx))) {
            w.add(Widget.weekBars("minutos", "Minutos de actividad por semana", "Meta OMS: 150 min", "min",
                    metrics.weekly(uw, "minutos_sesion", 8, "sum"), 150.0));
        }
        List<Bar> bars = new ArrayList<>();
        metrics.latest(uw, "hea_dolor_despues").ifPresent(v -> bars.add(new Bar("Dolor bajo control", Math.max(0, 100 - v * 10), fmt(v) + "/10 al terminar")));
        metrics.latest(uw, "hea_funcion").ifPresent(v -> bars.add(new Bar("Función diaria", v * 10, fmt(v) + "/10 en tu actividad más difícil")));
        metrics.latest(uw, "hea_adherencia").ifPresent(v -> bars.add(new Bar("Constancia", v, fmt(v) + " % esta semana")));
        metrics.latest(uw, "hea_equilibrio_etapa").ifPresent(v -> bars.add(new Bar("Equilibrio", v / 4 * 100, "Etapa " + fmt(v) + " de 4")));
        metrics.latest(uw, "hea_chair_stand").ifPresent(v -> bars.add(new Bar("Fuerza de piernas", Math.min(100, v / 15 * 100), fmt(v) + " repeticiones en 30 s")));
        if (!bars.isEmpty()) w.add(Widget.bars("funcion", "Tu función hoy", "0–100 (más es mejor)", bars));
        else w.add(Widget.notice("sin_datos", "info", "Aún no hay datos", "Registra tu dolor en cada sesión y haz los tests para ver tu evolución aquí."));
        return w;
    }

    /** Ids de sesiones cuyo dolor de 24 h ya se aplicó (para no contarlo dos veces). */
    static List<String> handledNextDay(WorldContext ctx) {
        Object v = ctx.world().stateValue("nextDayHandled");
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> l) for (Object o : l) out.add(String.valueOf(o));
        return out;
    }

    @Override
    public SessionOutcome onNextDayPain(WorldContext ctx, java.util.UUID sessionLogId, int pain) {
        List<String> handled = handledNextDay(ctx);
        if (handled.contains(sessionLogId.toString())) return SessionOutcome.empty();
        SessionSummary s = sessionLogs.recent(ctx.world().id(), 20).stream()
                .filter(x -> x.id().equals(sessionLogId)).findFirst().orElse(null);
        Integer baseline = s == null ? null : s.painBefore();
        ProgressionInfo prog = progression(ctx);
        int phase = phaseOf(ctx, prog);
        PainMonitoring.State before = new PainMonitoring.State(phase, intState(ctx, "greenStreak"),
                intState(ctx, "redStreak"), volumeFactor(ctx));
        Decision d = PainMonitoring.decideNextDay(baseline, pain, before, PainMonitoring.GREEN_MAX);

        SessionOutcome.Builder out = SessionOutcome.builder();
        handled.add(sessionLogId.toString());
        while (handled.size() > 20) handled.remove(0);
        out.state("nextDayHandled", handled);
        String base = baseline == null ? "" : " (antes de la sesión: " + baseline + "/10)";
        switch (d.light()) {
            case VERDE -> out.highlight("🟢 Tu dolor a las 24 h (" + pain + "/10) volvió a tu nivel habitual" + base + ". ¡Vas bien!");
            case AMARILLO -> {
                out.highlight("🟡 Tu dolor a las 24 h (" + pain + "/10) quedó algo por encima de lo habitual" + base
                        + ": mantenemos la fase y bajamos el volumen al " + Math.round(d.state().volumeFactor() * 100) + " %.");
                out.state("lastLight", "amarillo");
            }
            case ROJO -> {
                out.highlight("🔴 Tu dolor a las 24 h (" + pain + "/10) subió demasiado" + base + (d.regressed()
                        ? ": volvemos a " + (prog == null ? "la fase anterior" : prog.step(d.state().phase()).name()) + " y bajamos el volumen."
                        : ": seguimos en la fase 1 con menos volumen."));
                out.highlight(d.referAdvised()
                        ? "Es el segundo semáforo rojo seguido: te recomendamos consultar a un profesional de la salud antes de continuar."
                        : "Si el dolor no baja en 24–48 h, consulta a un profesional.");
                out.state("lastLight", "rojo");
                if (d.referAdvised()) out.state("referAdvised", true);
                out.metric("hea_semaforo", Light.ROJO.code);
            }
            case SIN_DATOS -> {
            }
        }
        if (d.light() == Light.AMARILLO) out.metric("hea_semaforo", Light.AMARILLO.code);
        if (d.light() == Light.AMARILLO || d.light() == Light.ROJO) {
            out.state("phase", d.state().phase()).state("greenStreak", d.state().greenStreak())
                    .state("redStreak", d.state().redStreak()).state("volumeFactor", d.state().volumeFactor())
                    .state("greenRun", 0).state("lastLightOn", LocalDate.now(MetricRepository.ZONE).toString());
            if (d.state().phase() != phase) {
                out.step(progKey(track(ctx)), d.state().phase());
                out.level(d.state().phase() + 1);
            }
            out.regeneratePlan();
        }
        return out.build();
    }

    /** En Salud la progresión la guía el semáforo de dolor: no se permiten saltos de fase manuales. */
    @Override
    public boolean allowsManualSteps() {
        return false;
    }

    @Override
    public SessionUi sessionUi() {
        return new SessionUi("protocol", List.of("pain"), List.of("pain", "rpe"), List.of("reps", "hold", "pain"), List.of(
                "Muévete solo en el rango cómodo: molestia leve (≤ 3/10) está bien, dolor no.",
                "Si aparece un dolor punzante o que sube de 5/10, detén el ejercicio.",
                "Respira durante todo el movimiento; no contengas el aire.",
                "Lento y controlado: el tempo 3-1-3 es 3 s de ida, 1 de pausa y 3 de vuelta.",
                "Mañana te preguntaremos cómo amaneciste: es clave para decidir si avanzas."));
    }

    @Override
    public String assistantPersona(WorldContext ctx) {
        ProgressionInfo prog = progression(ctx);
        int phase = phaseOf(ctx, prog);
        String phaseName = prog == null ? "Fase " + (phase + 1) : prog.step(phase).name();
        String last = ctx.world().stateString("lastLight");
        return """
                Eres un acompañante educativo de ejercicio para la salud y la rehabilitación, prudente y empático.
                NO eres médico ni fisioterapeuta: nunca diagnostiques, nunca interpretes síntomas ni sugieras medicamentos,
                y no cambies lo que indique el profesional tratante del usuario. Ante dolor intenso, hinchazón, fiebre,
                pérdida de fuerza o sensibilidad, cambios en la vejiga o el intestino, dolor en el pecho o una caída fuerte,
                recomienda consultar a un profesional (o urgencias si es grave) y no propongas ejercicio.
                El usuario sigue la ruta "%s" en %s; su último semáforo de dolor fue "%s" y su volumen está al %d %%.
                Reglas del semáforo: dolor ≤ 3/10 durante y a las 24 h = verde (suma para avanzar); 4–5 = amarillo (se
                mantiene la fase y baja el volumen); > 5 o 24 h con 3+ puntos sobre su base = rojo (retrocede una fase).
                Explica los ejercicios con lenguaje sencillo, anima a moverse en el rango cómodo y a registrar el dolor.
                Basa tus explicaciones en guías públicas (OMS, CDC STEADI, NIA) y recuerda que el programa es educativo.%s
                """.formatted(track(ctx), phaseName, last == null ? "sin datos" : last, Math.round(volumeFactor(ctx) * 100),
                ctx.user().health().mustRefer()
                        ? "\nIMPORTANTE: el usuario marcó señales de alarma; insiste en que consulte antes de progresar."
                        : "");
    }

    // ───────────────────────── Utilidades ─────────────────────────

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double num(Object o, double fallback) {
        return o instanceof Number n ? n.doubleValue() : fallback;
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
