package com.sportacus.backend.world.core;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pieza del inicio o del progreso de un mundo. El frontend tiene un componente por {@code type};
 * cada mundo decide qué widgets mostrar y en qué orden, así cada sector tiene un dashboard de nicho
 * sin duplicar pantallas. Tipos y forma de {@code data}: ver docs/16-API-MUNDOS.md §Widgets.
 */
public record Widget(String type, String key, String title, String subtitle, Map<String, Object> data) {

    // ── Piezas de datos ───────────────────────────────────────

    public record Stat(String label, String value, String unit, String hint, String tone) {
    }

    public record Point(LocalDate date, double value) {
    }

    public record Bar(String label, double value, String detail) {
    }

    public record Axis(String label, double value, String detail) {
    }

    public record Zone(double from, double to, String tone, String label) {
    }

    public record TreeStep(int index, String name, String status) {
    }

    public record Tree(String key, String name, String icon, int current, int total, List<TreeStep> steps) {
    }

    public record Insight(String icon, String title, String body, String tone, String actionLabel, String actionRoute) {
        public static Insight of(String icon, String title, String body, String tone) {
            return new Insight(icon, title, body, tone, null, null);
        }
    }

    public record Phase(String name, String status, String detail) {
    }

    // ── Fábricas ──────────────────────────────────────────────

    private static Map<String, Object> data(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] != null) m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** Próxima sesión del plan (o null si no hay). */
    public static Widget nextSession(String title, Object session) {
        return new Widget("next_session", "next_session", title, null, data("session", session));
    }

    public static Widget statRow(String key, String title, List<Stat> stats) {
        return new Widget("stat_row", key, title, null, data("stats", stats));
    }

    /** Serie temporal. direction: "up" (más es mejor) o "down" (menos es mejor). */
    public static Widget line(String key, String title, String subtitle, String unit, List<Point> points,
            String direction, Double target) {
        return new Widget("line", key, title, subtitle,
                data("unit", unit, "points", points, "direction", direction, "target", target));
    }

    /** Barras verticales por semana/día con objetivo opcional. */
    public static Widget weekBars(String key, String title, String subtitle, String unit, List<Bar> bars, Double target) {
        return new Widget("week_bars", key, title, subtitle, data("unit", unit, "bars", bars, "target", target));
    }

    /** Barras horizontales 0–100. */
    public static Widget bars(String key, String title, String subtitle, List<Bar> bars) {
        return new Widget("bars", key, title, subtitle, data("bars", bars));
    }

    /** Radar con ejes 0–100. */
    public static Widget radar(String key, String title, String subtitle, List<Axis> axes) {
        return new Widget("radar", key, title, subtitle, data("axes", axes));
    }

    public static Widget gauge(String key, String title, String subtitle, double value, double min, double max,
            String unit, List<Zone> zones, String caption) {
        return new Widget("gauge", key, title, subtitle,
                data("value", value, "min", min, "max", max, "unit", unit, "zones", zones, "caption", caption));
    }

    public static Widget skillTree(String key, String title, String subtitle, List<Tree> trees) {
        return new Widget("skill_tree", key, title, subtitle, data("trees", trees));
    }

    /** Semáforo. level: verde | amarillo | rojo | sin_datos. */
    public static Widget trafficLight(String key, String title, String level, Double value, String unit, String message) {
        return new Widget("traffic_light", key, title, null,
                data("level", level, "value", value, "unit", unit, "message", message));
    }

    public static Widget goal(String key, String title, String label, double progressPct, String detail,
            LocalDate targetDate) {
        return new Widget("goal", key, title, null, data("label", label, "progressPct", progressPct, "detail", detail,
                "targetDate", targetDate));
    }

    public static Widget insights(String title, List<Insight> items) {
        return new Widget("insights", "insights", title, null, data("items", items));
    }

    public static Widget phaseTrack(String key, String title, String subtitle, List<Phase> phases) {
        return new Widget("phase_track", key, title, subtitle, data("phases", phases));
    }

    /** Aviso destacado (p. ej. descargo de responsabilidad en Salud). tone: info | warn | danger | success. */
    public static Widget notice(String key, String tone, String title, String body) {
        return new Widget("notice", key, title, null, data("tone", tone, "body", body));
    }

    // ── Perfil 360 y Mi Base (docs/19) ────────────────────────

    /** Anillo de "qué tan bien te conocemos" (% global y por área). Toca: /profile360. */
    public static Widget profileRing(int pct, String level, String label, List<?> areas) {
        return new Widget("profile_ring", "profile_ring", "Tu perfil", null,
                data("pct", pct, "level", level, "label", label, "areas", areas));
    }

    /** Tarjeta de una pregunta sugerida (o de consentimiento, chequeo o afinar plan) para responder sin salir. */
    public static Widget question(Object nextItem) {
        return new Widget("question", "question", null, null, data("item", nextItem));
    }

    /** Invitación al check-in diario (ánimo, sueño, energía, estrés, dolor). */
    public static Widget checkin(boolean done, Object readiness) {
        return new Widget("checkin", "checkin", "¿Cómo llegas hoy?", null, data("done", done, "readiness", readiness));
    }

    /** Disposición de hoy tras el check-in: factor de volumen y motivos. */
    public static Widget readiness(double factor, String label, List<String> reasons) {
        return new Widget("readiness", "readiness", "Tu día", null,
                data("factor", factor, "label", label, "reasons", reasons));
    }

    /** Mundos sugeridos con sus motivos. */
    public static Widget worldSuggestions(String title, String subtitle, List<?> items) {
        return new Widget("world_suggestions", "world_suggestions", title, subtitle, data("items", items));
    }
}
