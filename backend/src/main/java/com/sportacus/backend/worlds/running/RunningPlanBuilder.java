package com.sportacus.backend.worlds.running;

import static com.sportacus.backend.worlds.running.RunningMath.duration;
import static com.sportacus.backend.worlds.running.RunningMath.km;
import static com.sportacus.backend.worlds.running.RunningMath.pace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.sportacus.backend.world.core.Factor;
import com.sportacus.backend.world.core.GeneratedPlan;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedBlock;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedSession;
import com.sportacus.backend.world.core.Prescription;
import com.sportacus.backend.worlds.running.RunningMath.Paces;

/**
 * Constructor puro del plan de running (sin Spring ni BD, probado en RunningPlanBuilderTest).
 * <ul>
 * <li><b>RUN</b>: periodización por semanas — volumen +≤10 %/semana, descarga (−25 %) cada 4ª semana,
 * 80/20 (1–2 sesiones de calidad, el resto suave), tirada larga en el día preferido y afinamiento final.</li>
 * <li><b>WALK_RUN</b>: progresión caminar-correr de 9 semanas (estructura inspirada en Couch to 5K del NHS,
 * redactada con texto propio).</li>
 * <li><b>WALK</b>: caminata progresiva de bajo impacto (lesión de miembro inferior moderada/grave o derivación).</li>
 * </ul>
 * El plan empieza en la semana de programa {@code startWeek} (índice 0 del plan guardado), así regenerar a
 * mitad del programa no lo reinicia.
 */
public final class RunningPlanBuilder {

    public enum Mode { WALK, WALK_RUN, RUN }

    /** Movimientos que suman kilómetros de carrera. */
    static final Set<String> RUN_KEYS = Set.of("trote_suave", "tirada_larga", "carrera_tempo", "intervalos_carrera",
            "cuestas_carrera", "caminar_trotar", "zancadas_aceleracion");
    static final Set<String> WALK_KEYS = Set.of("caminata_rapida", "caminata_cuestas");
    static final int WALK_PACE = 600; // 10:00 /km, solo para estimar km planificados de caminata

    /**
     * @param totalWeeks     semanas del programa (de la ruta o hasta la fecha de carrera)
     * @param startWeek      semana de programa en curso (0-based)
     * @param weekdays       días de entrenamiento (1=lun … 7=dom), ya con el día de tirada larga incluido
     * @param longDay        día de la tirada larga (o null = el último)
     * @param startKm        km semanales actuales
     * @param paces          ritmos desde el 5K (null = por sensaciones)
     * @param defaultEasy    ritmo suave supuesto (s/km) para convertir km en minutos si no hay ritmos
     * @param quality        se permiten sesiones de calidad (intervalos, tempo, cuestas)
     * @param easyChanges    sin calidad pero con "rodaje con cambios" moderado (impacto restringido con aval)
     * @param useDistance    la tirada larga se prescribe en km (tiene GPS/móvil y ritmos)
     * @param allowed        movimientos utilizables (seguros) del mundo
     * @param targetKm       distancia de la carrera objetivo (null = sin carrera)
     * @param minutes        minutos disponibles por sesión entre semana
     * @param restriction    motivo de la restricción de impacto/intensidad (texto) o null
     */
    public record Input(String track, String trackName, Mode mode, int totalWeeks, int startWeek, int sessions,
            List<Integer> weekdays, Integer longDay, double startKm, Paces paces, int defaultEasy, boolean quality,
            boolean easyChanges, boolean useDistance, Set<String> allowed, Double targetKm, int minutes,
            String restriction) {
    }

    /** @param weekKm km planificados por semana de PROGRAMA (índice = semana de programa) */
    public record Output(GeneratedPlan plan, List<Double> weekKm) {
    }

    private final Input in;
    private boolean capped;
    private int maxSessions = 0;
    private int minSessions = Integer.MAX_VALUE;
    private int maxQuality = 0;

    private RunningPlanBuilder(Input in) {
        this.in = in;
    }

    public static Output build(Input in) {
        return new RunningPlanBuilder(in).run();
    }

    private Output run() {
        return switch (in.mode()) {
            case WALK -> walkPlan();
            case WALK_RUN -> walkRunPlan();
            case RUN -> runPlan();
        };
    }

    // ───────────────────────── Fases y volumen ─────────────────────────

    public static boolean raceTrack(String track) {
        return "mejorar_5k".equals(track) || "10k".equals(track) || "media_maraton".equals(track);
    }

    /** Factor de afinamiento: media maratón 2 semanas (80 % y 60 %), resto 1 semana (60 %). */
    static double taperFactor(int w, int total, String track) {
        if (w == total - 1) return 0.6;
        if ("media_maraton".equals(track) && w == total - 2) return 0.8;
        return 1;
    }

    static double peakKm(String track, double startKm) {
        return switch (track) {
            case "mejorar_5k" -> Math.max(startKm, 35);
            case "10k" -> Math.max(startKm, 45);
            case "media_maraton" -> Math.max(startKm, 55);
            default -> Math.max(startKm * 1.3, 15);
        };
    }

    static double longCap(String track) {
        return switch (track) {
            case "mejorar_5k" -> 10;
            case "10k" -> 14;
            case "media_maraton" -> 19;
            default -> 10;
        };
    }

    /** Volumen objetivo por semana de programa. */
    static double[] volumes(String track, int weeks, double startKm) {
        boolean taper = raceTrack(track);
        double peak = peakKm(track, startKm);
        double build = startKm;
        double lastBuild = startKm;
        double[] v = new double[weeks];
        for (int w = 0; w < weeks; w++) {
            double tf = taper ? taperFactor(w, weeks, track) : 1;
            if (tf < 1) {
                v[w] = lastBuild * tf;
            } else if ((w + 1) % 4 == 0) {
                v[w] = build * 0.75; // descarga
            } else {
                if (w > 0) build = Math.min(peak, build * 1.10);
                lastBuild = build;
                v[w] = build;
            }
        }
        return v;
    }

    static String phaseName(int w, int total, String track) {
        if (raceTrack(track) && taperFactor(w, total, track) < 1) return "Afinamiento";
        double f = (w + 1) / (double) total;
        if (f <= 0.34) return "Base";
        if (f <= 0.67) return "Desarrollo";
        return raceTrack(track) ? "Específico" : "Consolidación";
    }

    // ───────────────────────── RUN ─────────────────────────

    private enum Kind { EASY, LONG, INTERVALS, TEMPO, HILLS, CHANGES, RACE }

    private Output runPlan() {
        int total = Math.max(1, in.totalWeeks());
        double[] vol = volumes(in.track(), total, in.startKm());
        boolean maintenance = in.startWeek() >= total;
        List<GeneratedSession> sessions = new ArrayList<>();
        List<Double> weekKm = new ArrayList<>();
        // Se simula siempre desde la semana 0 (así regenerar a mitad de programa da las mismas semanas) y la regla
        // del 10 % se aplica sobre los km REALES planificados de la última semana de construcción.
        double lastBuild = 0;
        double peak = 0;
        for (int w = 0; w < total; w++) {
            double tf = raceTrack(in.track()) ? taperFactor(w, total, in.track()) : 1;
            boolean recovery = tf >= 1 && (w + 1) % 4 == 0;
            double target = w == 0 ? vol[0] : tf < 1 ? lastBuild * tf : recovery ? lastBuild * 0.75
                    : Math.min(vol[w], lastBuild * 1.10);
            List<GeneratedSession> week = runWeek(w, total, target, false, w - in.startWeek());
            double sum = week.stream().mapToDouble(s -> sessionKm(s, in.defaultEasy())).sum();
            if (tf >= 1 && !recovery) {
                lastBuild = sum;
                peak = Math.max(peak, sum);
            }
            weekKm.add(round1(sum));
            if (w >= in.startWeek()) sessions.addAll(week);
        }
        if (maintenance) sessions.addAll(runWeek(total, total, peak * 0.85, true, 0));
        List<Factor> why = new ArrayList<>();
        if (maintenance) {
            why.add(Factor.of("mantenimiento", 0, "Programa completado: semana de mantenimiento a ~" + km(peak * 0.85)
                    + " km mientras eliges una nueva meta"));
        } else {
            why.add(Factor.of("volumen", 0, "Volumen de " + km(weekKm.get(0)) + " a " + km(peak)
                    + " km/semana, subiendo como máximo un 10 % por semana"));
            if (total >= 4) why.add(Factor.of("descarga", 0, "Cada 4ª semana es de descarga (−25 % de volumen) para asimilar"));
            if (raceTrack(in.track())) {
                why.add(Factor.of("afinamiento", 0, "media_maraton".equals(in.track())
                        ? "Afinamiento de 2 semanas (80 % y 60 % del volumen) antes de la carrera"
                        : "Afinamiento: la última semana baja al 60 % del volumen y termina con la carrera"));
            }
        }
        int q = maxQuality;
        if (maxSessions > 0 && minSessions < maxSessions) {
            why.add(Factor.of("frecuencia", 0, "De " + minSessions + " a " + maxSessions
                    + " salidas por semana: se añaden días a medida que sube el volumen"));
        }
        why.add(Factor.of("distribucion", 0, q > 0
                ? "Distribución 80/20: " + q + (q == 1 ? " sesión" : " sesiones") + " de calidad por semana y el resto a ritmo suave"
                : in.easyChanges() ? "Todo a intensidad baja-moderada: los días de calidad son rodajes con cambios suaves"
                        : "Todo a intensidad suave: construimos base aeróbica sin esfuerzos duros"));
        if (in.paces() != null) {
            Paces p = in.paces();
            why.add(Factor.of("ritmos", 0, "Ritmos desde tu 5K de " + RunningMath.time(p.fiveKS()) + ": suave " + p.easyRange()
                    + ", tempo " + pace(p.tempo()) + ", intervalos " + pace(p.interval()) + " /km"));
        } else {
            why.add(Factor.of("ritmos", 0, "Sin marca de referencia: intensidad por sensaciones (prueba del habla). "
                    + "Haz el 5K contrarreloj o el test de Cooper para calcular tus ritmos"));
        }
        double longest = sessions.stream().filter(s -> "tirada_larga".equals(s.kind()))
                .mapToDouble(s -> sessionKm(s, in.defaultEasy())).max().orElse(0);
        if (longest > 0) {
            why.add(Factor.of("tirada_larga", 0, "Tirada larga los " + dayName(longDay()).toLowerCase() + ", hasta "
                    + km(longest) + " km"));
        }
        if (in.restriction() != null) why.add(Factor.of("seguridad", 0, in.restriction()));
        if (capped) why.add(Factor.of("tiempo", 0, "Los rodajes entre semana se ajustan a tus " + in.minutes() + " minutos por sesión"));
        if (in.targetKm() != null && "media_maraton".equals(in.track()) && longest > 0 && longest < 15 && !maintenance) {
            why.add(Factor.of("aviso", 0, "Con tu volumen actual la tirada más larga llega a " + km(longest)
                    + " km: completarás la media maratón alternando correr y caminar el final, o elige una fecha más lejana"));
        }
        String title = maintenance ? "Mantenimiento · " + in.trackName() : "Plan " + in.trackName() + " · " + total + " semanas";
        String summary = maintenance ? "Semana tipo que se repite: mantienes tu forma hasta elegir una nueva meta."
                : "Periodizado: base → desarrollo → " + (raceTrack(in.track()) ? "específico → afinamiento" : "consolidación")
                        + ". Volumen +≤10 %/semana, descarga cada 4 semanas y 80 % del tiempo a ritmo suave.";
        int weeks = maintenance ? 1 : total - in.startWeek();
        return new Output(new GeneratedPlan(title, summary, weeks, null, why, sessions), weekKm);
    }

    int qualityPerWeek(int n) {
        if (!in.quality() || "base_aerobica".equals(in.track())) return 0;
        if (n >= 5) return 2;
        if (n >= 3) return 1;
        return 0;
    }

    /** Elige {@code n} días de {@code all} incluyendo siempre el de tirada larga y repartiendo el resto. */
    static List<Integer> pickDays(List<Integer> all, int longDay, int n) {
        if (n >= all.size()) return all;
        List<Integer> others = new ArrayList<>(all);
        others.remove(Integer.valueOf(longDay));
        int k = n - 1;
        TreeSet<Integer> out = new TreeSet<>();
        out.add(longDay);
        if (k == 1) out.add(others.get(0));
        else for (int j = 0; j < k; j++) out.add(others.get((int) Math.round(j * (others.size() - 1) / (double) (k - 1))));
        return new ArrayList<>(out);
    }

    private int longDay() {
        if (in.longDay() != null && in.weekdays().contains(in.longDay())) return in.longDay();
        return in.weekdays().get(in.weekdays().size() - 1);
    }

    /** Semana de RUN: reparte el volumen entre tirada larga, calidad y rodajes suaves. */
    private List<GeneratedSession> runWeek(int w, int total, double v, boolean maintenance, int planWeek) {
        String track = in.track();
        boolean taperWeek = !maintenance && raceTrack(track) && taperFactor(w, total, track) < 1;
        boolean raceWeek = !maintenance && raceTrack(track) && w == total - 1;
        boolean recovery = !maintenance && !taperWeek && (w + 1) % 4 == 0;
        int stage = maintenance ? 3 : buildStage(w);
        int avail = Math.max(1, Math.min(in.sessions(), in.weekdays().size()));
        // Con poco volumen, menos sesiones y más largas (~5 km de media, mínimo 3): no fragmentar los km.
        int n = Math.min(avail, Math.max(Math.min(avail, 3), (int) Math.floor(v / 5.0)));
        maxSessions = Math.max(maxSessions, n);
        minSessions = Math.min(minSessions, n);
        int q = qualityPerWeek(n);
        maxQuality = Math.max(maxQuality, (recovery || taperWeek) ? 0 : q);
        boolean changes = q == 0 && in.easyChanges() && !"base_aerobica".equals(track) && n >= 3;
        if (recovery || taperWeek) q = Math.min(q, 1);

        // Tipos por día.
        int ld = longDay();
        List<Integer> days = pickDays(in.weekdays().stream().sorted().limit(avail).toList(), ld, n);
        Kind[] kinds = new Kind[days.size()];
        for (int i = 0; i < kinds.length; i++) kinds[i] = Kind.EASY;
        int li = days.indexOf(ld);
        if (li < 0) li = days.size() - 1;
        kinds[li] = raceWeek ? Kind.RACE : Kind.LONG;
        int hard = changes ? 1 : q;
        List<Kind> qualityKinds = qualityKinds(stage, q, recovery, taperWeek, changes);
        // Calidad en los días más alejados de la tirada larga (nunca la víspera).
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < days.size(); i++) if (i != li) order.add(i);
        final int longIdx = li;
        order.sort(Comparator.comparingInt((Integer i) -> (days.get(i) == days.get(longIdx) - 1) ? 1 : 0)
                .thenComparingInt(i -> i));
        // Si hay 2 de calidad, que no queden en días consecutivos cuando hay alternativa.
        List<Integer> picked = new ArrayList<>();
        for (int i : order) {
            if (picked.size() >= Math.min(hard, qualityKinds.size())) break;
            if (picked.stream().noneMatch(j -> Math.abs(days.get(j) - days.get(i)) == 1)) picked.add(i);
        }
        for (int i : order) {
            if (picked.size() >= Math.min(hard, qualityKinds.size())) break;
            if (!picked.contains(i)) picked.add(i);
        }
        picked.sort(Comparator.naturalOrder());
        for (int k = 0; k < picked.size(); k++) kinds[picked.get(k)] = qualityKinds.get(k);
        int easyCount = 0;
        for (Kind k : kinds) if (k == Kind.EASY) easyCount++;

        // Km de cada tipo.
        int easyPace = in.paces() != null ? in.paces().easy() : in.defaultEasy();
        boolean half = "media_maraton".equals(track);
        double share = n <= 3 ? (half ? 0.40 : 0.38) : n == 4 ? (half ? 0.36 : 0.33) : (half ? 0.33 : 0.30);
        double minLong = "base_aerobica".equals(track) ? 3 : 4;
        double longKm = clamp(v * share, minLong, longCap(track));
        if (taperWeek && !raceWeek) longKm = clamp(v * 0.33, 4, longCap(track) * 0.7);
        double qualityKm = 0;
        List<GeneratedSession> built = new ArrayList<>();
        // Primero calidad y carrera; luego se reparte lo que queda entre la tirada larga y los rodajes.
        GeneratedSession[] out = new GeneratedSession[days.size()];
        for (int i = 0; i < days.size(); i++) {
            GeneratedSession s = switch (kinds[i]) {
                case RACE -> race();
                case INTERVALS -> intervals(stage, recovery, taperWeek, v);
                case TEMPO -> tempo(stage, recovery, taperWeek, v);
                case HILLS -> hills(stage, recovery);
                case CHANGES -> changes(stage, recovery);
                default -> null;
            };
            if (s != null) {
                out[i] = s;
                qualityKm += sessionKm(s, in.defaultEasy());
            }
        }
        double easyFloor = 2.5;
        double easyKm = easyCount == 0 ? 0 : (v - qualityKm - (kinds[li] == Kind.LONG ? longKm : 0)) / easyCount;
        if (easyCount > 0 && easyKm < easyFloor) {
            easyKm = easyFloor;
            if (kinds[li] == Kind.LONG) longKm = clamp(v - qualityKm - easyFloor * easyCount, minLong, longKm);
        }
        if (kinds[li] == Kind.LONG) out[li] = longRun(longKm, easyPace);
        int easyIdx = 0;
        for (int i = 0; i < days.size(); i++) {
            if (out[i] != null) continue;
            boolean strides = easyIdx == 0 && !recovery && !taperWeek && in.quality() && allowed("zancadas_aceleracion")
                    && stage >= 1;
            boolean strength = easyIdx == easyCount - 1 && !raceWeek;
            out[i] = easyRun(easyKm, easyPace, strides, strength, recovery);
            easyIdx++;
        }
        // Ajuste fino: si los mínimos (rodajes de 2,5 km, progresiones) pasan del objetivo, recorta la tirada larga.
        double sum = 0;
        for (GeneratedSession s : out) sum += sessionKm(s, in.defaultEasy());
        if (kinds[li] == Kind.LONG && sum > v + 0.1) {
            out[li] = longRun(Math.max(minLong, longKm - (sum - v)), easyPace);
        }
        String phase = maintenance ? "Mantenimiento" : phaseName(w, total, track);
        String weekNote = maintenance ? "Semana de mantenimiento"
                : "Semana " + (w + 1) + " de " + total + " · " + phase + (recovery ? " · semana de descarga" : "");
        for (int i = 0; i < days.size(); i++) {
            GeneratedSession s = out[i];
            String notes = s.notes() == null ? weekNote : weekNote + ". " + s.notes();
            built.add(new GeneratedSession(planWeek, i, days.get(i), s.title(), s.focus(), s.kind(), null, notes, s.blocks()));
        }
        return built;
    }

    /** Número de semanas de construcción (no descarga ni afinamiento) anteriores a {@code w}. */
    int buildStage(int w) {
        int s = 0;
        for (int i = 0; i < w; i++) if ((i + 1) % 4 != 0) s++;
        return s;
    }

    private List<Kind> qualityKinds(int stage, int q, boolean recovery, boolean taper, boolean changes) {
        List<Kind> out = new ArrayList<>();
        if (changes) {
            out.add(Kind.CHANGES);
            return out;
        }
        String t = in.track();
        boolean hillsOk = allowed("cuestas_carrera");
        boolean intOk = allowed("intervalos_carrera");
        boolean tempoOk = allowed("carrera_tempo");
        Kind primary;
        if (taper) primary = intOk ? Kind.INTERVALS : Kind.TEMPO;
        else if (recovery) primary = tempoOk ? Kind.TEMPO : Kind.INTERVALS;
        else if (!"mejorar_5k".equals(t) && stage < 2 && hillsOk) primary = Kind.HILLS;
        else if ("media_maraton".equals(t)) primary = stage % 2 == 0 ? Kind.TEMPO : Kind.INTERVALS;
        else primary = stage % 2 == 0 ? Kind.INTERVALS : Kind.TEMPO;
        out.add(primary);
        if (q >= 2) out.add(primary == Kind.TEMPO ? (intOk ? Kind.INTERVALS : Kind.HILLS) : Kind.TEMPO);
        out.removeIf(k -> (k == Kind.INTERVALS && !intOk) || (k == Kind.TEMPO && !tempoOk) || (k == Kind.HILLS && !hillsOk));
        return out;
    }

    // ── Sesiones RUN ──

    private GeneratedSession easyRun(double kmTarget, int easyPace, boolean strides, boolean strength, boolean light) {
        int dur = roundTo((int) (kmTarget * easyPace), 60);
        int cap = Math.max(20, in.minutes() - 10) * 60;
        if (dur > cap) {
            dur = cap;
            capped = true;
        }
        dur = Math.max(dur, 15 * 60);
        List<GeneratedBlock> b = new ArrayList<>();
        b.add(GeneratedBlock.of("calentamiento", "movilidad_dinamica_corredor", "Movilidad dinámica",
                Prescription.time(240, "suave")));
        b.add(GeneratedBlock.of("principal", "trote_suave", "Rodaje suave", easyPrescription(dur)));
        if (strides) {
            b.add(GeneratedBlock.of("tecnica", "zancadas_aceleracion", "Progresiones",
                    Prescription.interval(5, 20, 60, "rápido y relajado (no esprint)").withCue("Vuelve caminando entre cada una")));
        }
        if (strength) b.addAll(strengthBlocks(light ? 2 : 3));
        b.addAll(coolDown());
        double k = dur / (double) easyPace;
        String title = in.paces() != null ? "Rodaje suave " + km(k) + " km" : "Rodaje suave " + dur / 60 + " min";
        return new GeneratedSession(0, 0, null, title, "≈ " + km(k) + " km · zona suave" + (strength ? " + fuerza" : ""),
                "rodaje", null, null, b);
    }

    private Prescription easyPrescription(int durationS) {
        Paces p = in.paces();
        return p == null ? Prescription.time(durationS, "conversacional (RPE 3–4)").withCue("Prueba del habla: debes poder decir frases completas")
                : Prescription.time(durationS, "suave " + p.easyRange()).withPace(p.easy());
    }

    private GeneratedSession longRun(double kmTarget, int easyPace) {
        Paces p = in.paces();
        int pace = p != null ? p.longRun() : in.defaultEasy() + 10;
        double k = Math.max(1, Math.floor(kmTarget * 2) / 2.0); // medio km, redondeo hacia abajo
        Prescription pr;
        if (in.useDistance() && p != null) {
            pr = Prescription.distance((int) Math.round(k * 1000), pace, "suave " + p.easyRange());
        } else {
            int dur = Math.max(600, (int) Math.floor(k * pace / 60.0) * 60);
            pr = Prescription.time(dur, p != null ? "suave " + p.easyRange() : "conversacional (RPE 3–4)");
            if (p != null) pr = pr.withPace(pace);
            k = dur / (double) pace;
        }
        List<GeneratedBlock> b = new ArrayList<>();
        b.add(GeneratedBlock.of("calentamiento", "movilidad_dinamica_corredor", "Movilidad dinámica", Prescription.time(240, "suave")));
        b.add(GeneratedBlock.of("principal", "tirada_larga", "Tirada larga", pr.withCue("Los primeros 10 minutos, aún más lento")));
        b.addAll(coolDown());
        int minutes = (int) Math.round(k * pace / 60);
        String title = in.paces() != null || in.useDistance() ? "Tirada larga " + km(k) + " km" : "Tirada larga " + minutes + " min";
        String notes = minutes >= 60 ? "Lleva agua: bebe a sorbos cada 20–30 minutos." : null;
        return new GeneratedSession(0, 0, null, title, "≈ " + km(k) + " km · ritmo suave", "tirada_larga", null, notes, b);
    }

    private static final int[][] INT_5K = { { 6, 400 }, { 8, 400 }, { 5, 800 }, { 6, 800 }, { 4, 1000 }, { 5, 1000 } };
    private static final int[][] INT_10K = { { 5, 800 }, { 6, 800 }, { 4, 1000 }, { 5, 1000 }, { 6, 1000 }, { 4, 1600 } };
    private static final int[][] INT_HALF = { { 5, 1000 }, { 6, 1000 }, { 3, 1600 }, { 4, 1600 }, { 3, 2000 }, { 4, 2000 } };

    private GeneratedSession intervals(int stage, boolean recovery, boolean taper, double weekKm) {
        int[][] table = switch (in.track()) {
            case "10k" -> INT_10K;
            case "media_maraton" -> INT_HALF;
            default -> INT_5K;
        };
        int idx = Math.min(table.length - 1, "mejorar_5k".equals(in.track()) ? stage : stage / 2 + 1);
        int reps = table[idx][0];
        int dist = table[idx][1];
        if (recovery) reps = Math.max(3, reps - 2);
        // Volumen de intervalos ≤ ~15 % del semanal (mínimo 3 repeticiones) para no desbalancear el 80/20 con poco volumen.
        reps = Math.min(reps, Math.max(3, (int) Math.floor(weekKm * 150 / dist)));
        if (taper) {
            reps = 4;
            dist = "media_maraton".equals(in.track()) ? 1000 : 400;
        }
        int recover = dist <= 400 ? 90 : dist <= 1000 ? 120 : 180;
        Paces p = in.paces();
        Integer pace = p == null ? null : dist >= 1600 ? p.race() + 8 : p.interval();
        int defaultRace = in.defaultEasy() - 75;
        int workS = roundTo((int) (dist / 1000.0 * (pace != null ? pace : defaultRace)), 15);
        Prescription pr;
        String label;
        if (p != null) {
            pr = new Prescription("interval", null, null, null, null, null, null, null, null, dist, reps, workS, recover,
                    null, pace, "intervalo " + pace(pace) + " /km (RPE 8)", null, null, null,
                    "Recupera trotando muy suave");
            label = reps + "×" + distLabel(dist);
        } else {
            pr = Prescription.interval(reps, workS, recover, "fuerte pero sostenible (RPE 8)").withCue("Recupera trotando muy suave");
            label = reps + "×" + duration(workS);
        }
        List<GeneratedBlock> b = qualityWarmUp();
        b.add(GeneratedBlock.of("intervalo", "intervalos_carrera", "Intervalos " + label, pr));
        b.addAll(qualityCoolDown());
        return new GeneratedSession(0, 0, null, "Intervalos " + label, "Velocidad · " + label, "intervalos", null,
                taper ? "Afinamiento: rápido pero corto, para llegar fresco." : null, b);
    }

    private GeneratedSession tempo(int stage, boolean recovery, boolean taper, double weekKm) {
        int base = switch (in.track()) {
            case "10k" -> 15 + 3 * stage;
            case "media_maraton" -> 20 + 3 * stage;
            default -> 12 + 2 * stage;
        };
        int cap = switch (in.track()) {
            case "10k" -> 30;
            case "media_maraton" -> 40;
            default -> 24;
        };
        int tempoPace = in.paces() != null ? in.paces().tempo() : in.defaultEasy() - 50;
        // Tramo tempo ≤ ~20 % del volumen semanal (mínimo 12 min).
        int min = Math.min(Math.min(cap, base), Math.max(12, (int) Math.round(weekKm * 0.20 * tempoPace / 60)));
        if (recovery) min = Math.max(10, min - 8);
        if (taper) min = 10;
        Paces p = in.paces();
        Prescription pr = Prescription.time(min * 60, p == null ? "cómodamente duro (RPE 7): frases cortas"
                : "tempo " + pace(p.tempo()) + " /km (RPE 7)");
        if (p != null) pr = pr.withPace(taper && in.targetKm() != null && in.targetKm() > 5 ? racePace(p) : p.tempo());
        List<GeneratedBlock> b = qualityWarmUp();
        b.add(GeneratedBlock.of("principal", "carrera_tempo", "Tempo " + min + " min", pr.withCue("Uniforme de principio a fin")));
        b.addAll(qualityCoolDown());
        return new GeneratedSession(0, 0, null, "Tempo " + min + " min", "Umbral · " + min + " min cómodamente duro",
                "tempo", null, null, b);
    }

    private GeneratedSession hills(int stage, boolean recovery) {
        int reps = Math.min(10, 6 + stage);
        if (recovery) reps = Math.max(4, reps - 2);
        List<GeneratedBlock> b = qualityWarmUp();
        b.add(GeneratedBlock.of("intervalo", "cuestas_carrera", "Cuestas " + reps + "×60 s",
                Prescription.interval(reps, 60, 90, "subida fuerte (RPE 8), bajada trotando").withCue("Baja despacio: cuida las rodillas")));
        b.addAll(qualityCoolDown());
        return new GeneratedSession(0, 0, null, "Cuestas " + reps + "×60 s", "Fuerza específica en subida", "cuestas", null,
                null, b);
    }

    private GeneratedSession changes(int stage, boolean recovery) {
        int reps = Math.min(8, 4 + stage / 2);
        if (recovery) reps = Math.max(3, reps - 2);
        List<GeneratedBlock> b = new ArrayList<>();
        b.add(GeneratedBlock.of("calentamiento", "trote_suave", "Calentamiento: trote suave", easyPrescription(600)));
        b.add(GeneratedBlock.of("intervalo", "trote_suave", "Cambios " + reps + "×2 min",
                Prescription.interval(reps, 120, 120, "un poco más vivo (RPE 5–6), sin esprintar")
                        .withCue("Recupera a ritmo suave, sin parar")));
        b.add(GeneratedBlock.of("vuelta_calma", "trote_suave", "Vuelta a la calma: trote suave", easyPrescription(300)));
        b.addAll(stretches());
        return new GeneratedSession(0, 0, null, "Rodaje con cambios " + reps + "×2 min",
                "Ritmo moderado sin impacto extra", "rodaje_cambios", null, null, b);
    }

    private GeneratedSession race() {
        double dist = in.targetKm() == null ? 5 : in.targetKm();
        Paces p = in.paces();
        String movement = allowed("carrera_tempo") ? "carrera_tempo" : "trote_suave";
        Integer pace = p == null ? null : racePace(p);
        Prescription pr = Prescription.distance((int) Math.round(dist * 1000), pace,
                p == null ? "tu ritmo de carrera: exigente pero uniforme" : "ritmo de carrera " + pace(pace) + " /km");
        List<GeneratedBlock> b = new ArrayList<>();
        b.add(GeneratedBlock.of("calentamiento", "trote_suave", "Calentamiento: trote suave", easyPrescription(600)));
        if (allowed("zancadas_aceleracion")) {
            b.add(GeneratedBlock.of("tecnica", "zancadas_aceleracion", "Progresiones", Prescription.interval(3, 20, 60, "rápido y relajado")));
        }
        b.add(GeneratedBlock.of("principal", movement, "Carrera " + km(dist) + " km",
                pr.withCue("Sal controlado: el primer km, un poco más lento que tu ritmo objetivo")));
        b.addAll(stretches());
        String name = dist >= 21 ? "media maratón" : km(dist) + "K";
        return new GeneratedSession(0, 0, null, "Día de carrera: " + name, "Tu objetivo · " + km(dist) + " km", "prueba",
                null, "mejorar_5k".equals(in.track()) ? "Si no hay carrera oficial, regístralo como 5K contrarreloj en Tests."
                        : "¡A disfrutarla! Hidrátate y no estrenes nada el día de la carrera.", b);
    }

    private int racePace(Paces p) {
        double d = in.targetKm() == null ? 5 : in.targetKm();
        if (d >= 21) return p.half();
        if (d >= 10) return p.tenK();
        return p.race();
    }

    private List<GeneratedBlock> qualityWarmUp() {
        List<GeneratedBlock> b = new ArrayList<>();
        b.add(GeneratedBlock.of("calentamiento", "trote_suave", "Calentamiento: trote suave", easyPrescription(600)));
        if (allowed("drills_carrera")) {
            b.add(GeneratedBlock.of("tecnica", "drills_carrera", "Técnica de carrera",
                    Prescription.time(300, "técnica").withCue("2 × 20 m de cada ejercicio")));
        }
        return b;
    }

    private List<GeneratedBlock> qualityCoolDown() {
        List<GeneratedBlock> b = new ArrayList<>();
        b.add(GeneratedBlock.of("vuelta_calma", "trote_suave", "Vuelta a la calma: trote suave", easyPrescription(480)));
        b.addAll(stretches());
        return b;
    }

    private List<GeneratedBlock> coolDown() {
        return stretches();
    }

    private List<GeneratedBlock> stretches() {
        List<GeneratedBlock> b = new ArrayList<>();
        b.add(GeneratedBlock.of("vuelta_calma", "estiramiento_gemelo_soleo", "Estiramiento de gemelo y sóleo",
                Prescription.time(120, "suave").withSide("cada_lado")));
        b.add(GeneratedBlock.of("vuelta_calma", "movilidad_cadera_corredor", "Movilidad de cadera",
                Prescription.time(120, "suave").withSide("cada_lado")));
        return b;
    }

    private List<GeneratedBlock> strengthBlocks(int sets) {
        List<GeneratedBlock> b = new ArrayList<>();
        if (allowed("elevacion_talones")) b.add(GeneratedBlock.of("accesorio", "elevacion_talones", "Elevación de talones",
                Prescription.reps(sets, 15, 45).withCue("Sube rápido, baja en 3 segundos")));
        if (allowed("puente_gluteo")) b.add(GeneratedBlock.of("accesorio", "puente_gluteo", "Puente de glúteo",
                Prescription.reps(sets, 12, 45)));
        if (allowed("plancha")) b.add(GeneratedBlock.of("accesorio", "plancha", "Plancha", Prescription.hold(sets, 30, 30)));
        return b;
    }

    // ───────────────────────── WALK_RUN (caminar-correr) ─────────────────────────

    /** Segmentos por semana: {correr s, caminar s, repeticiones}; una lista por sesión (o una para las tres). */
    static final int[][][][] WALK_RUN = {
            { { { 60, 90, 8 } } },
            { { { 90, 120, 6 } } },
            { { { 90, 90, 2 }, { 180, 180, 2 } } },
            { { { 180, 90, 2 }, { 300, 150, 2 } } },
            { { { 300, 180, 3 } }, { { 480, 300, 2 } }, { { 1200, 0, 1 } } },
            { { { 300, 180, 2 }, { 480, 0, 1 } }, { { 600, 180, 2 } }, { { 1500, 0, 1 } } },
            { { { 1500, 0, 1 } } },
            { { { 1680, 0, 1 } } },
            { { { 1800, 0, 1 } } } };

    private Output walkRunPlan() {
        int total = Math.min(WALK_RUN.length, Math.max(1, in.totalWeeks()));
        boolean maintenance = in.startWeek() >= total;
        List<GeneratedSession> sessions = new ArrayList<>();
        List<Integer> days = in.weekdays().stream().sorted().limit(3).toList();
        int first = maintenance ? WALK_RUN.length - 1 : in.startWeek();
        int last = maintenance ? first : total - 1;
        List<Double> weekKm = new ArrayList<>();
        for (int w = 0; w < total; w++) weekKm.add(0.0);
        for (int w = first; w <= last; w++) {
            double sum = 0;
            for (int d = 0; d < days.size(); d++) {
                int[][][] week = WALK_RUN[w];
                int[][] segs = week[Math.min(d, week.length - 1)];
                GeneratedSession s = walkRunSession(segs, d == 1);
                String note = maintenance ? "Mantienes tus 30 minutos: puedes pasar a la ruta Mejorar mi 5K"
                        : "Semana " + (w + 1) + " de " + total + " · progresión caminar-correr inspirada en Couch to 5K (NHS)";
                if (!maintenance && w == total - 1 && d == days.size() - 1 && total == WALK_RUN.length) {
                    s = new GeneratedSession(0, 0, null, "¡Tus 30 minutos seguidos!", s.focus(), s.kind(), null, null, s.blocks());
                }
                sessions.add(new GeneratedSession(w - first, d, days.get(d), s.title(), s.focus(), s.kind(), null, note,
                        s.blocks()));
                sum += sessionKm(s, in.defaultEasy());
            }
            if (!maintenance) weekKm.set(w, round1(sum));
        }
        List<Factor> why = new ArrayList<>();
        why.add(Factor.of("progresion", 0, "Progresión de " + total + " semanas de caminar-correr, de 1 minuto de trote a "
                + (total == WALK_RUN.length ? "30 minutos seguidos" : "tramos más largos") + " (estructura inspirada en Couch to 5K del NHS)"));
        why.add(Factor.of("frecuencia", 0, "3 sesiones por semana con un día de descanso entre ellas para que tendones y articulaciones se adapten"));
        why.add(Factor.of("intensidad", 0, "El trote es muy suave: si no puedes hablar, vas demasiado rápido"));
        if (in.restriction() != null) why.add(Factor.of("seguridad", 0, in.restriction()));
        why.add(Factor.of("repetir", 0, "Si una semana se te hace dura, repítela: no hay prisa"));
        String title = maintenance ? "Mantenimiento · 30 minutos corriendo" : in.trackName() + " · caminar-correr " + total + " semanas";
        String summary = "Cada semana un poco más de trote y menos caminata. 5 minutos de caminata rápida antes y después.";
        return new Output(new GeneratedPlan(title, summary, maintenance ? 1 : total - in.startWeek(), null, why, sessions), weekKm);
    }

    private GeneratedSession walkRunSession(int[][] segs, boolean strength) {
        List<GeneratedBlock> b = new ArrayList<>();
        b.add(GeneratedBlock.of("calentamiento", "caminata_rapida", "Caminata rápida", Prescription.time(300, "caminata vigorosa")));
        boolean continuous = segs.length == 1 && segs[0][1] == 0;
        String runMove = allowed("trote_suave") ? "trote_suave" : "caminar_trotar";
        int runS = 0;
        for (int[] s : segs) {
            runS += s[0] * s[2];
            if (s[1] == 0 && s[2] == 1) {
                b.add(GeneratedBlock.of("principal", runMove, "Trote continuo " + duration(s[0]),
                        Prescription.time(s[0], "trote muy suave, conversacional")));
            } else {
                b.add(GeneratedBlock.of("intervalo", "caminar_trotar",
                        s[2] + "×(" + duration(s[0]) + " trote / " + duration(s[1]) + " caminata)",
                        Prescription.interval(s[2], s[0], s[1], "trote suave / caminata rápida")));
            }
        }
        if (strength) b.addAll(strengthBlocks(2));
        b.add(GeneratedBlock.of("vuelta_calma", "caminata_rapida", "Caminata suave", Prescription.time(300, "suave")));
        b.addAll(stretches());
        String title = continuous ? "Carrera continua " + duration(segs[0][0])
                : segs.length == 1 ? "Caminar y trotar " + segs[0][2] + "×(" + duration(segs[0][0]) + " / " + duration(segs[0][1]) + ")"
                        : "Caminar y trotar · " + duration(runS) + " de trote";
        return new GeneratedSession(0, 0, null, title, duration(runS) + " de trote en total", continuous ? "rodaje" : "caminar_correr",
                null, null, b);
    }

    // ───────────────────────── WALK (sin impacto de carrera) ─────────────────────────

    private Output walkPlan() {
        int total = Math.max(1, in.totalWeeks());
        boolean maintenance = in.startWeek() >= total;
        int n = Math.max(2, Math.min(4, in.sessions()));
        List<Integer> days = in.weekdays().stream().sorted().limit(n).toList();
        List<GeneratedSession> sessions = new ArrayList<>();
        List<Double> weekKm = new ArrayList<>();
        for (int w = 0; w < total; w++) weekKm.add(0.0);
        int first = maintenance ? total : in.startWeek();
        int last = maintenance ? total : total - 1;
        for (int w = first; w <= last; w++) {
            int base = Math.min(50, 20 + 3 * Math.min(w, total - 1));
            if (!maintenance && (w + 1) % 4 == 0) base = (int) (base * 0.8);
            double sum = 0;
            for (int d = 0; d < days.size(); d++) {
                boolean longOne = d == days.size() - 1;
                boolean hills = d == 0 && w >= 2 && allowed("caminata_cuestas");
                int minutes = Math.min(in.minutes() > 0 && !longOne ? Math.max(20, in.minutes() - 5) : 75,
                        longOne ? (int) Math.round(base * 1.3) : base);
                GeneratedSession s = walkSession(minutes, hills, w, d == 1);
                String note = maintenance ? "Semana de mantenimiento" : "Semana " + (w + 1) + " de " + total + " · caminata progresiva"
                        + ((w + 1) % 4 == 0 ? " · semana de descarga" : "");
                sessions.add(new GeneratedSession(w - first, d, days.get(d), s.title(), s.focus(), s.kind(), null, note, s.blocks()));
                sum += sessionKm(s, in.defaultEasy());
            }
            if (!maintenance) weekKm.set(w, round1(sum));
        }
        List<Factor> why = new ArrayList<>();
        why.add(Factor.of("bajo_impacto", 0, in.restriction() != null ? in.restriction()
                : "Plan de caminata: bajo impacto mientras tu cuerpo se prepara para correr"));
        why.add(Factor.of("progresion", 0, "De 20 a 50 minutos de caminata rápida, +3 min por semana y descarga cada 4 semanas"));
        why.add(Factor.of("oms", 0, "Apunta a 150–300 minutos semanales de actividad moderada (OMS, 2020)"));
        String title = maintenance ? "Mantenimiento · caminata" : in.trackName() + " · caminata progresiva " + total + " semanas";
        return new Output(new GeneratedPlan(title, "Caminata rápida y en cuesta, sin el impacto de correr. Cuando el dolor ceda, podrás pasar a caminar-correr.",
                maintenance ? 1 : total - in.startWeek(), null, why, sessions), weekKm);
    }

    private GeneratedSession walkSession(int minutes, boolean hills, int w, boolean strength) {
        List<GeneratedBlock> b = new ArrayList<>();
        b.add(GeneratedBlock.of("calentamiento", "movilidad_dinamica_corredor", "Movilidad dinámica", Prescription.time(240, "suave")));
        String title;
        if (hills) {
            int reps = Math.min(8, 4 + w / 2);
            int rest = Math.max(10 * 60, minutes * 60 - reps * 180);
            b.add(GeneratedBlock.of("principal", "caminata_rapida", "Caminata rápida", Prescription.time(rest, "vigorosa: respiras fuerte pero hablas")));
            b.add(GeneratedBlock.of("intervalo", "caminata_cuestas", "Cuestas caminando " + reps + "×90 s",
                    Prescription.interval(reps, 90, 90, "subida firme / llano suave")));
            title = "Caminata con cuestas " + minutes + " min";
        } else {
            b.add(GeneratedBlock.of("principal", "caminata_rapida", "Caminata rápida",
                    Prescription.time(minutes * 60, "vigorosa: respiras fuerte pero hablas")));
            title = "Caminata rápida " + minutes + " min";
        }
        if (strength) b.addAll(strengthBlocks(2));
        b.addAll(stretches());
        return new GeneratedSession(0, 0, null, title, "Bajo impacto · " + minutes + " min", "caminata", null, null, b);
    }

    // ───────────────────────── Utilidades ─────────────────────────

    private boolean allowed(String key) {
        return in.allowed() == null || in.allowed().contains(key);
    }

    /** Km planificados de una sesión (solo bloques de carrera y la caminata principal). */
    public static double sessionKm(GeneratedSession s, int defaultEasy) {
        double sum = 0;
        for (GeneratedBlock b : s.blocks()) sum += blockKm(b.movementKey(), b.blockType(), b.prescription(), defaultEasy);
        return sum;
    }

    /** Km planificados de un bloque. Caminata: solo si es el bloque principal (10:00 /km). */
    public static double blockKm(String movement, String blockType, Prescription p, int defaultEasy) {
        if (movement == null || p == null) return 0;
        boolean walk = WALK_KEYS.contains(movement);
        if (walk && !("principal".equals(blockType) || "intervalo".equals(blockType))) return 0;
        if (!walk && !RUN_KEYS.contains(movement)) return 0;
        int pace = walk ? WALK_PACE : p.paceSecPerKm() != null ? p.paceSecPerKm() : defaultEasy;
        return switch (p.type()) {
            case "distance" -> p.distanceM() == null ? 0 : p.distanceM() / 1000.0;
            case "time" -> p.durationS() == null ? 0 : p.durationS() / (double) pace;
            case "interval" -> {
                int reps = p.repeats() == null ? 1 : p.repeats();
                double work = p.distanceM() != null ? p.distanceM() / 1000.0
                        : (p.workS() == null ? 0 : p.workS()) / (double) (walk ? WALK_PACE : Math.max(150, defaultEasy - 60));
                boolean jogRecovery = !"caminar_trotar".equals(movement) && !"zancadas_aceleracion".equals(movement);
                double rec = jogRecovery && p.recoverS() != null ? p.recoverS() / (double) (walk ? WALK_PACE : defaultEasy + 60) : 0;
                yield reps * (work + rec);
            }
            default -> 0;
        };
    }

    /** Días de entrenamiento con el de tirada larga incluido. */
    public static List<Integer> withLongDay(List<Integer> days, Integer longDay) {
        if (longDay == null || days.contains(longDay)) return days.stream().sorted().toList();
        TreeSet<Integer> set = new TreeSet<>(days);
        // Sustituye el día más cercano (que no sea la víspera de otra sesión dura) por el día de tirada larga.
        Integer closest = set.stream().min(Comparator.comparingInt(d -> Math.abs(d - longDay))).orElse(null);
        if (closest != null) set.remove(closest);
        set.add(longDay);
        return new ArrayList<>(set);
    }

    static String dayName(int weekday) {
        return switch (weekday) {
            case 1 -> "Lunes";
            case 2 -> "Martes";
            case 3 -> "Miércoles";
            case 4 -> "Jueves";
            case 5 -> "Viernes";
            case 6 -> "Sábados";
            default -> "Domingos";
        };
    }

    static String distLabel(int m) {
        return m >= 1000 && m % 1000 == 0 ? (m / 1000) + " km" : m >= 1000 ? km(m / 1000.0) + " km" : m + " m";
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int roundTo(int v, int step) {
        return Math.max(step, Math.round(v / (float) step) * step);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
