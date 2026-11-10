package com.sportacus.backend.worlds.base;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.core.Factor;
import com.sportacus.backend.world.core.GeneratedPlan;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedBlock;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedSession;
import com.sportacus.backend.world.core.Prescription;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.core.UserProfileData;

/**
 * Plan "Movimiento diario" de Mi Base (reglas puras, sin BD). Se basa en las directrices de la OMS (2020): 150–300 min
 * de actividad moderada a la semana, fortalecimiento muscular 2 días y, desde los 65 años, equilibrio; y en las
 * directrices de MinSalud Colombia. Funciona sin ningún dato (3 sesiones cortas, moderadas, sin equipo y sin impacto)
 * y se adapta a medida que el usuario cuenta cosas. Cada decisión deja una razón legible que cita el dato.
 */
public final class BasePlanBuilder {

    public static final int WHO_WEEKLY_MIN = 150;
    public static final String WALK = "caminata";
    public static final String STRENGTH = "fuerza";
    public static final String MOBILITY = "movilidad";
    public static final String GENTLE = "suave";
    public static final String MIXED = "mixta";

    private static final String MODERATE = "moderada";
    private static final String TALK_TEST = "Ritmo moderado: puedes hablar, pero no cantar";

    private BasePlanBuilder() {
    }

    /**
     * @param movements   movimientos de Mi Base que el usuario puede hacer (clave → nombre), ya filtrados por seguridad
     * @param weekdays    días sugeridos (1=lunes … 7=domingo), uno por sesión
     * @param weeksActive semanas completas desde que entró a Mi Base (para la progresión de la caminata)
     */
    public record Input(UserProfileData profile, HealthSummary health, ProfileInsights insights,
            Map<String, String> movements, List<Integer> weekdays, int weeksActive) {

        public Input {
            if (profile == null) profile = UserProfileData.empty();
            if (health == null) health = HealthSummary.unknown();
            if (insights == null) insights = ProfileInsights.empty();
            if (movements == null) movements = Map.of();
            if (weekdays == null) weekdays = List.of();
        }
    }

    // ───────────────────────── Parámetros ─────────────────────────

    /** Sesiones por semana: lo que dijo el usuario o 3 si aún no lo sabemos. */
    public static int sessionsPerWeek(UserProfileData profile) {
        return clamp(profile.daysOr(3), 1, 7);
    }

    /** Minutos por sesión: 20 si no lo sabemos; menos con hijos pequeños, trabajo físico o señales de alarma. */
    public static int sessionMinutes(UserProfileData profile, HealthSummary health, ProfileInsights in) {
        int m = profile.minutesOr(20);
        if (in.youngChildren()) m = clamp(m, 10, 20);
        if (in.physicalJob()) m = Math.min(m, 30);
        if (health.mustRefer()) m = Math.min(m, 15);
        return clamp(m, 10, 60);
    }

    /** Fracción de la caminata objetivo: empieza al 70 % y sube un 10 % por semana activa. */
    static double walkRamp(int weeksActive) {
        return Math.min(1.0, 0.7 + 0.1 * Math.max(0, weeksActive));
    }

    static boolean needsMobility(ProfileInsights in) {
        return in.physicalJob() || (in.sittingHours() != null && in.sittingHours() >= 8);
    }

    /** Tipo de cada sesión de la semana: fuerza 2 días (OMS), caminata el resto, movilidad si la necesita. */
    public static List<String> kinds(int days, boolean gentle, boolean mobility) {
        List<String> out = new ArrayList<>();
        if (gentle) {
            for (int d = 0; d < days; d++) out.add(GENTLE);
            return out;
        }
        if (days == 1) return List.of(MIXED);
        if (days == 2) return List.of(STRENGTH, WALK);
        if (days == 3) return List.of(STRENGTH, WALK, STRENGTH);
        for (int d = 0; d < days; d++) out.add(d % 2 == 1 ? STRENGTH : WALK);
        // Con 5+ días, fuerza solo 2 veces (el resto, caminata).
        int strength = 0;
        for (int d = 0; d < days; d++) {
            if (STRENGTH.equals(out.get(d)) && ++strength > 2) out.set(d, WALK);
        }
        if (mobility) {
            int last = out.lastIndexOf(WALK);
            if (last >= 0) out.set(last, MOBILITY);
        }
        return out;
    }

    // ───────────────────────── Plan ─────────────────────────

    public static GeneratedPlan build(Input in) {
        UserProfileData p = in.profile();
        HealthSummary h = in.health();
        ProfileInsights ins = in.insights();
        boolean gentle = h.mustRefer();
        boolean mobility = needsMobility(ins);
        int days = sessionsPerWeek(p);
        int minutes = sessionMinutes(p, h, ins);
        List<String> kinds = kinds(days, gentle, mobility);
        Ctx c = new Ctx(in, minutes, mobility, p.ageOr(0) >= 65);

        List<GeneratedSession> sessions = new ArrayList<>();
        for (int d = 0; d < kinds.size(); d++) {
            Integer weekday = d < in.weekdays().size() ? in.weekdays().get(d) : null;
            Built b = switch (kinds.get(d)) {
                case GENTLE -> gentle(c);
                case STRENGTH -> strength(c, false);
                case MIXED -> strength(c, true);
                case MOBILITY -> mobility(c);
                default -> walk(c);
            };
            sessions.add(new GeneratedSession(0, d, weekday, b.title, b.focus, kinds.get(d), null, b.notes, b.blocks));
        }

        String summary = gentle
                ? "Sesiones muy suaves de respiración, movilidad y caminata corta mientras consultas a un profesional."
                : days + (days == 1 ? " sesión" : " sesiones") + " de ~" + minutes + " min: " + describe(kinds)
                        + ". Registra también cualquier otra actividad: todo suma a tus " + WHO_WEEKLY_MIN + " minutos.";
        return new GeneratedPlan("Movimiento diario", summary, 1, null, rationale(in, days, minutes, mobility), sessions);
    }

    private static String describe(List<String> kinds) {
        List<String> parts = new ArrayList<>();
        long walks = kinds.stream().filter(k -> WALK.equals(k)).count();
        long strength = kinds.stream().filter(k -> STRENGTH.equals(k) || MIXED.equals(k)).count();
        long mob = kinds.stream().filter(k -> MOBILITY.equals(k)).count();
        if (walks > 0) parts.add(walks + (walks == 1 ? " caminata" : " caminatas"));
        if (strength > 0) parts.add(strength + " de fuerza general");
        if (mob > 0) parts.add(mob + " de movilidad y pausas activas");
        return String.join(", ", parts);
    }

    static List<Factor> rationale(Input in, int days, int minutes, boolean mobility) {
        UserProfileData p = in.profile();
        HealthSummary h = in.health();
        ProfileInsights ins = in.insights();
        List<Factor> f = new ArrayList<>();
        f.add(Factor.of("oms", 0, "Basado en la OMS (2020) y MinSalud: 150–300 min de actividad moderada a la semana"
                + " y fortalecer los músculos 2 días"));
        if (h.mustRefer()) {
            f.add(Factor.of("seguridad", 0, "Marcaste señales que un profesional debe revisar: por ahora solo respiración,"
                    + " movilidad suave y caminatas cortas"));
        }
        f.add(Factor.of("dias", 0, p.daysPerWeek() == null
                ? "Como aún no sabemos cuántos días tienes, empezamos con 3"
                : "Nos dijiste que tienes " + days + (days == 1 ? " día" : " días") + " a la semana"));
        if (p.minutesPerSession() == null) {
            f.add(Factor.of("minutos", 0, "Como aún no sabemos tu tiempo, empezamos con sesiones cortas de ~" + minutes + " min"));
        } else if (minutes != p.minutesPerSession()) {
            f.add(Factor.of("minutos", 0, "Sesiones de ~" + minutes + " min (nos dijiste " + p.minutesPerSession()
                    + "): " + minutesReason(h, ins)));
        } else {
            f.add(Factor.of("minutos", 0, "Sesiones de ~" + minutes + " min, el tiempo que nos dijiste que tienes"));
        }
        if (walkRamp(in.weeksActive()) < 1.0 && !h.mustRefer()) {
            f.add(Factor.of("progresion", 0, "Las caminatas empiezan al " + Math.round(walkRamp(in.weeksActive()) * 100)
                    + " % de tu tiempo y crecen cada semana: poco a poco es más fácil sostenerlo"));
        }
        if (h.prudentMode() && !h.mustRefer()) {
            f.add(Factor.of("prudente", 0, "Intensidad moderada y sin impacto mientras completas tu chequeo de salud"));
        }
        if (ins.lowBudget()) {
            f.add(Factor.of("equipo", 0, "Como prefieres no gastar, todo es sin equipo: tu peso, una silla y una mochila"));
        } else if (p.environments().isEmpty() && p.equipment().isEmpty()) {
            f.add(Factor.of("equipo", 0, "Como aún no sabemos dónde entrenas, todo es con tu peso corporal y se puede hacer en casa"));
        }
        if (p.ageOr(0) >= 65) {
            f.add(Factor.of("edad", 0, "Como tienes " + p.age() + " años, sumamos equilibrio varios días (la OMS lo recomienda desde los 65)"));
        }
        if (ins.hasDog()) f.add(Factor.of("perro", 0, "Como tienes perro, tus caminatas pueden ser con él"));
        if (ins.youngChildren()) f.add(Factor.of("hijos", 0, "Como tienes hijos pequeños, sesiones cortas que puedes hacer en casa"));
        if (ins.heatHumid()) f.add(Factor.of("clima", 0, "Como tu clima es cálido, te sugerimos horarios frescos e hidratación"));
        if (ins.unsafeAtNight()) f.add(Factor.of("seguridad_noche", 0, "Como tu zona no es segura de noche, caminatas de día o en casa"));
        if (ins.noJumpingAtHome()) f.add(Factor.of("sin_saltos", 0, "Como no puedes saltar en casa, el plan no tiene saltos"));
        if (ins.physicalJob()) f.add(Factor.of("trabajo", 0, "Como tu trabajo ya es físico, menos volumen y más movilidad"));
        if (ins.sittingHours() != null && ins.sittingHours() >= 8) {
            f.add(Factor.of("sentado", 0, "Como pasas ~" + ins.sittingHours() + " h sentado, sumamos pausas activas y movilidad de cadera y espalda alta"));
        }
        if (ins.activeCommuteMinWeek() != null && ins.activeCommuteMinWeek() > 0) {
            int rest = Math.max(0, WHO_WEEKLY_MIN - ins.activeCommuteMinWeek());
            f.add(Factor.of("transporte", 0, "Tus ~" + ins.activeCommuteMinWeek() + " min semanales de transporte activo ya cuentan"
                    + (rest == 0 ? ": ya cumples los 150 de la OMS, el plan suma fuerza y bienestar"
                            : ": te faltan ~" + rest + " para los 150")));
        }
        if (mobility && days < 4 && !h.mustRefer()) {
            f.add(Factor.of("movilidad", 0, "Cada sesión cierra con movilidad de cadera y espalda alta"));
        }
        return f;
    }

    private static String minutesReason(HealthSummary h, ProfileInsights ins) {
        if (h.mustRefer()) return "hasta que consultes, sesiones más cortas";
        if (ins.youngChildren()) return "con hijos pequeños, mejor sesiones cortas en casa";
        if (ins.physicalJob()) return "tu trabajo ya suma carga";
        return "ajustado a un rango seguro";
    }

    // ───────────────────────── Sesiones ─────────────────────────

    private record Ctx(Input in, int minutes, boolean mobility, boolean older) {
        ProfileInsights ins() {
            return in.insights();
        }

        HealthSummary health() {
            return in.health();
        }

        boolean has(String key) {
            return in.movements().containsKey(key);
        }

        String first(String... keys) {
            for (String k : keys) if (has(k)) return k;
            return null;
        }

        GeneratedBlock block(String type, String key, Prescription rx, String note) {
            if (key == null || !has(key)) return null;
            GeneratedBlock b = GeneratedBlock.of(type, key, in.movements().get(key), rx);
            return note == null ? b : b.withNotes(note);
        }
    }

    private record Built(String title, String focus, String notes, List<GeneratedBlock> blocks) {
    }

    private static int seconds(List<GeneratedBlock> blocks) {
        return blocks.stream().mapToInt(b -> b.prescription().estimatedSeconds() + 30).sum();
    }

    private static void add(List<GeneratedBlock> blocks, GeneratedBlock b) {
        if (b != null) blocks.add(b);
    }

    private static String intensity(Ctx c) {
        return c.health().prudentMode() || !c.health().vigorousAllowed() ? MODERATE : "moderada (RPE 5–6)";
    }

    /** Caminata indoor si hay hijos pequeños; si no, a paso vivo (o suave con molestias de miembro inferior). */
    private static String walkKey(Ctx c) {
        if (c.ins().youngChildren()) return c.first("hea_marcha_estatica", "hea_caminata_suave");
        if (c.health().hasLowerLimbIssue()) return c.first("hea_caminata_suave", "hea_marcha_estatica");
        return c.first("hea_caminata_rapida", "hea_caminata_suave", "hea_marcha_estatica");
    }

    private static int walkSeconds(Ctx c, int otherSeconds) {
        double target = c.minutes() * 60.0 * walkRamp(c.in().weeksActive());
        if (c.ins().physicalJob()) target *= 0.8;
        int s = (int) Math.round((target - otherSeconds) / 60.0) * 60;
        return Math.max(c.ins().youngChildren() ? 600 : 480, s);
    }

    private static Built walk(Ctx c) {
        ProfileInsights ins = c.ins();
        List<GeneratedBlock> blocks = new ArrayList<>();
        add(blocks, c.block("calentamiento", c.first("hea_movilidad_articular_suave"), Prescription.time(120, "suave"), null));
        List<GeneratedBlock> tail = new ArrayList<>();
        if (c.older()) {
            add(tail, c.block("accesorio", c.first("hea_tandem_apoyo", "hea_equilibrio_un_pie_apoyo"),
                    Prescription.hold(2, 20, 30).withCue("Junto a una pared o mesón"), "Equilibrio: clave para prevenir caídas"));
        }
        if (c.mobility()) {
            add(tail, c.block("vuelta_calma", c.first("hea_estiramiento_flexores_cadera"),
                    Prescription.hold(2, 30, 15).withSide("cada_lado"), null));
        } else {
            add(tail, c.block("vuelta_calma", c.first("hea_estiramiento_pecho_pared", "hea_relajacion_guiada"),
                    Prescription.hold(2, 30, 15), null));
        }
        String key = walkKey(c);
        int walkS = walkSeconds(c, seconds(blocks) + seconds(tail));
        Prescription rx = Prescription.time(walkS, intensity(c)).withCue(TALK_TEST);
        add(blocks, c.block("principal", key, rx, null));
        blocks.addAll(tail);

        List<String> notes = new ArrayList<>();
        boolean indoor = "hea_marcha_estatica".equals(key);
        String title;
        if (ins.hasDog() && !indoor) {
            title = "Caminata con tu perro";
            notes.add("Como tienes perro, sácalo a caminar a paso vivo: es la forma más fácil de sumar minutos.");
        } else if (indoor && ins.youngChildren()) {
            title = "Marcha en casa";
            notes.add("Como tienes hijos pequeños, la hacemos en casa: marcha en el sitio de 10–20 min (¡invítalos!).");
        } else {
            title = "Caminata progresiva";
            notes.add("Caminar a paso vivo es la base de la OMS: cada minuto suma a tus 150 semanales.");
        }
        if (ins.heatHumid() && !indoor) notes.add("Como tu clima es cálido, camina temprano o al final de la tarde y lleva agua.");
        if (ins.unsafeAtNight() && !indoor) notes.add("Como tu zona no es segura de noche, camina de día o haz la marcha en casa.");
        if (c.older()) notes.add("Cerramos con equilibrio, siempre con apoyo cerca.");
        if (c.health().prudentMode()) notes.add(TALK_TEST + ".");
        return new Built(title, "Actividad aeróbica moderada", String.join(" ", notes), blocks);
    }

    private static Built strength(Ctx c, boolean mixed) {
        ProfileInsights ins = c.ins();
        int sets = ins.physicalJob() ? 1 : 2;
        int budget = c.minutes() * 60;
        String rpe = intensity(c);
        List<GeneratedBlock> blocks = new ArrayList<>();
        add(blocks, c.block("calentamiento", c.first("hea_movilidad_articular_suave"), Prescription.time(180, "suave"), null));
        if (!mixed) {
            add(blocks, c.block("calentamiento", c.first("hea_marcha_estatica"), Prescription.time(180, "suave a moderada"),
                    "Activa el cuerpo y suma minutos de actividad"));
        }
        Prescription base = Prescription.reps(sets, 8, 12, 60).withIntensity(rpe).withTempo("2-0-2");
        add(blocks, c.block("principal", c.first("hea_sentarse_levantarse", "hea_minisentadilla_apoyo"),
                base.withCue("Sin impulso; usa las manos si lo necesitas"), "Piernas: levantarte de la silla"));
        add(blocks, c.block("principal", c.first("flexion_inclinada", "hea_flexion_pared"), base, "Empuje: pecho y brazos"));
        add(blocks, c.block("principal", c.first("puente_gluteo", "hea_puente_gluteo"), base, "Glúteos y espalda baja"));
        add(blocks, c.block("principal", c.first("base_remo_mochila", "remo_bajo_mesa", "superman_y"), base,
                "Jale: espalda y postura"));
        if (c.older()) {
            add(blocks, c.block("accesorio", c.first("hea_equilibrio_un_pie_apoyo", "hea_tandem_apoyo"),
                    Prescription.hold(2, 20, 30).withSide("cada_lado").withCue("Junto a una pared o mesón"),
                    "Equilibrio: clave para prevenir caídas"));
        }
        List<GeneratedBlock> tail = new ArrayList<>();
        if (c.mobility()) {
            add(tail, c.block("vuelta_calma", c.first("hea_estiramiento_flexores_cadera"), Prescription.hold(2, 30, 15).withSide("cada_lado"), null));
            add(tail, c.block("vuelta_calma", c.first("hea_apertura_toracica_silla"), Prescription.reps(1, 8, 0), null));
        } else {
            add(tail, c.block("vuelta_calma", c.first("hea_relajacion_guiada"), Prescription.time(120, "suave"), null));
        }
        if (mixed) {
            int left = budget - seconds(blocks) - seconds(tail);
            int walkS = Math.max(480, left / 60 * 60);
            add(blocks, c.block("principal", walkKey(c), Prescription.time(walkS, rpe).withCue(TALK_TEST), "Suma minutos aeróbicos"));
        } else {
            // Accesorios solo si queda tiempo.
            for (GeneratedBlock extra : new GeneratedBlock[] {
                    c.block("accesorio", c.first("dead_bug"), Prescription.reps(sets, 6, 8, 45).withSide("cada_lado"), "Core"),
                    c.block("accesorio", c.first("elevacion_talones"), Prescription.reps(sets, 12, 15, 45), "Pantorrillas") }) {
                if (extra != null && seconds(blocks) + seconds(tail) + seconds(List.of(extra)) <= budget) blocks.add(extra);
            }
        }
        blocks.addAll(tail);

        List<String> notes = new ArrayList<>();
        notes.add("La OMS recomienda fortalecer los músculos 2 días por semana: aquí trabajas piernas, empuje, glúteos y espalda.");
        if (ins.physicalJob()) notes.add("Como tu trabajo ya es físico, haces 1 serie y más movilidad.");
        if (ins.lowBudget()) notes.add("Cero equipo: una silla firme y una mochila con libros.");
        if (ins.noJumpingAtHome()) notes.add("Sin saltos: puedes hacerla en casa sin molestar a nadie.");
        if (ins.youngChildren()) notes.add("Corta y en casa: puedes pausarla y retomarla.");
        if (c.older()) notes.add("Sumamos equilibrio: hazlo junto a una pared o mesón.");
        if (c.health().prudentMode()) notes.add("Intensidad moderada: termina cada serie con 2–3 repeticiones de reserva.");
        return new Built(mixed ? "Fuerza y caminata" : "Fuerza general", "Fortalecimiento muscular",
                String.join(" ", notes), blocks);
    }

    private static Built mobility(Ctx c) {
        ProfileInsights ins = c.ins();
        List<GeneratedBlock> blocks = new ArrayList<>();
        add(blocks, c.block("calentamiento", c.first("hea_respiracion_diafragmatica"), Prescription.time(120, "suave"), null));
        add(blocks, c.block("principal", c.first("hea_apertura_toracica_silla"), Prescription.reps(2, 8, 20), "Espalda alta"));
        add(blocks, c.block("principal", c.first("hea_rotacion_tronco_sentado"), Prescription.reps(2, 8, 20).withSide("cada_lado"), null));
        add(blocks, c.block("principal", c.first("hea_estiramiento_flexores_cadera"), Prescription.hold(2, 30, 15).withSide("cada_lado"), "Cadera"));
        add(blocks, c.block("principal", c.first("hea_retraccion_escapular"), Prescription.reps(2, 10, 20), "Postura"));
        add(blocks, c.block("principal", c.first("hea_gato_camello"), Prescription.reps(2, 8, 20), null));
        int left = c.minutes() * 60 - seconds(blocks);
        if (left >= 300) {
            add(blocks, c.block("principal", c.first("hea_caminata_suave", "hea_marcha_estatica"),
                    Prescription.time(left / 60 * 60, "suave"), "Caminata suave para cerrar"));
        }
        List<String> notes = new ArrayList<>();
        if (ins.sittingHours() != null && ins.sittingHours() >= 8) {
            notes.add("Como pasas ~" + ins.sittingHours() + " h sentado, movemos cadera y espalda alta."
                    + " Además, cada 30–60 min levántate 2–3 minutos (pausa activa).");
        }
        if (ins.physicalJob()) notes.add("Como tu trabajo ya es físico, este día es para soltar el cuerpo y recuperarte.");
        if (notes.isEmpty()) notes.add("Movilidad y pausas activas: menos rigidez y menos tiempo sentado.");
        return new Built("Movilidad y pausas activas", "Movilidad", String.join(" ", notes), blocks);
    }

    private static Built gentle(Ctx c) {
        List<GeneratedBlock> blocks = new ArrayList<>();
        add(blocks, c.block("calentamiento", c.first("hea_respiracion_diafragmatica"), Prescription.time(120, "suave"), null));
        add(blocks, c.block("principal", c.first("hea_movilidad_articular_suave"), Prescription.time(180, "suave"), null));
        GeneratedBlock cool = c.block("vuelta_calma", c.first("hea_relajacion_guiada"), Prescription.time(120, "suave"), null);
        String walk = c.ins().youngChildren() || c.ins().unsafeAtNight()
                ? c.first("hea_marcha_estatica", "hea_caminata_suave")
                : c.first("hea_caminata_suave", "hea_marcha_estatica");
        int left = c.minutes() * 60 - seconds(blocks) - (cool == null ? 0 : seconds(List.of(cool))) - 30;
        add(blocks, c.block("principal", walk, Prescription.time(Math.min(600, Math.max(300, left / 60 * 60)), "muy suave")
                .withCue("Debes poder conversar con comodidad"), null));
        add(blocks, cool);
        return new Built("Respira y muévete suave", "Movilidad suave",
                "Marcaste señales que un profesional debe revisar: por ahora solo respiración, movilidad suave y una"
                        + " caminata corta. Si notas dolor en el pecho, mareo o falta de aire, detente y consulta.",
                blocks.stream().filter(Objects::nonNull).toList());
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
