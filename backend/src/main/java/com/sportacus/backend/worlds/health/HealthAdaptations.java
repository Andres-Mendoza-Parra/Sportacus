package com.sportacus.backend.worlds.health;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.core.Factor;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.core.Widget.Insight;

/**
 * Reglas puras que adaptan el mundo Salud al chequeo (modo prudente, movilidad) y al Perfil 360 (medicamentos, trabajo,
 * horas sentado). Cada ajuste se explica citando el dato que lo motiva. Sin BD: probadas en {@code HealthAdaptationsTest}.
 */
public final class HealthAdaptations {

    /** Motivos que implican una molestia con fecha de inicio (la pregunta h_onset aplica). */
    static final Set<String> PAIN_REASONS = Set.of("dolor_sin_lesion", "lesion_reciente", "postoperatorio", "cronica");
    static final int SITTING_HOURS_TIP = 8;

    private HealthAdaptations() {
    }

    static boolean painReason(String reason) {
        return reason != null && PAIN_REASONS.contains(reason);
    }

    // ── Movilidad ──

    /** Silla de ruedas, ayuda para la marcha o amputación (chequeo de salud). */
    static boolean reducedMobility(HealthSummary h) {
        return h.hasFlag("movilidad_reducida") || h.has("movilidad_reducida") || wheelchair(h);
    }

    static boolean wheelchair(HealthSummary h) {
        return h.hasFlag("silla_ruedas");
    }

    /** Variante sentada (en silla) de un ejercicio, según su clave del catálogo. Los tests no cuentan. */
    static boolean isSeated(String movementKey) {
        if (movementKey == null || movementKey.startsWith("hea_test_")) return false;
        return movementKey.contains("sentado") || movementKey.contains("_silla");
    }

    /**
     * En silla de ruedas, solo variantes sentadas (si la ruta las tiene; si no, el conjunto original con aviso). Con otra
     * movilidad reducida, el conjunto no cambia: la preferencia se aplica como puntaje (ver {@link #seatedKeys}).
     */
    static <T> List<T> seatedOnlyIfWheelchair(List<T> pool, Function<T, String> key, boolean wheelchair) {
        if (!wheelchair) return pool;
        List<T> seated = pool.stream().filter(m -> isSeated(key.apply(m))).toList();
        return seated.isEmpty() ? pool : seated;
    }

    static <T> List<String> seatedKeys(List<T> pool, Function<T, String> key) {
        return pool.stream().map(key).filter(HealthAdaptations::isSeated).toList();
    }

    // ── Riesgo de caídas ──

    /**
     * Riesgo de caídas declarado o derivado del chequeo: caídas/inestabilidad (h_falls), movilidad reducida o baja visión.
     * No incluye el caso "sin respuesta": ese lo resuelven los tests STEADI.
     */
    static boolean declaredFallRisk(Answers a, HealthSummary h) {
        return a.yes("h_falls") || reducedMobility(h) || h.hasFlag("baja_vision");
    }

    /** Riesgo de caídas inicial: además del declarado, sin respuesta a h_falls y con 60+ años se asume riesgo (prudente). */
    static boolean initialFallRisk(Answers a, HealthSummary h, int age) {
        return declaredFallRisk(a, h) || (!a.has("h_falls") && age >= 60);
    }

    // ── Avisos de inscripción ──

    /** Avisos por datos que faltan, modo prudente y movilidad; cada uno dice qué supusimos y cómo afinarlo. */
    static List<String> enrollNotices(Answers a, HealthSummary h, int age) {
        List<String> out = new ArrayList<>();
        String reason = a.string("h_reason");
        if (painReason(reason) && !a.has("h_onset")) {
            out.add("Aún no sabemos hace cuánto empezó tu molestia: empezamos en la fase 1, la más suave. Cuéntanoslo en «Afina tu plan» para ajustarlo.");
        }
        if (!a.has("h_falls") && age >= 60 && !declaredFallRisk(a, h)) {
            out.add("No sabemos si te has caído o te sientes inestable: haremos el equilibrio siempre con apoyo. Haz los tests de STEADI (5 minutos) para afinarlo.");
        }
        if (h.prudentMode()) {
            out.add(h.moderateReason() + ". Mientras tanto, todo tu plan es suave y de bajo impacto.");
        }
        if (wheelchair(h)) {
            out.add("Como nos contaste en tu chequeo que usas silla de ruedas, tu plan usa solo variantes sentadas cuando la ruta las tiene. Si algún ejercicio no se adapta a ti, márcalo con «No me gusta» y lo cambiamos.");
        } else if (reducedMobility(h)) {
            out.add("Como nos contaste en tu chequeo que tienes movilidad reducida, priorizamos las variantes sentadas o con apoyo y el equilibrio siempre junto a una pared o mesón.");
        }
        if (h.hasFlag("baja_vision")) {
            out.add("Por tu baja visión, entrena en un espacio despejado y bien iluminado, y haz el equilibrio con apoyo firme.");
        }
        return out;
    }

    // ── Plan ──

    /** Factores de "por qué" del plan que nacen del chequeo y del Perfil 360. */
    static List<Factor> planFactors(String track, HealthSummary h, ProfileInsights in) {
        List<Factor> out = new ArrayList<>();
        if (h.prudentMode()) out.add(Factor.of("prudente", 0, h.moderateReason()));
        if (in.heartRateUnreliable()) {
            out.add(Factor.of("pulso", 0, "Como tomas betabloqueadores (según tu chequeo), tu pulso no refleja el esfuerzo: guiamos la intensidad por la prueba del habla y el esfuerzo percibido, no por pulsaciones"));
        }
        if (in.hypoglycemiaRisk()) {
            out.add(Factor.of("glucosa", 0, "Como usas insulina (según tu chequeo), cada sesión te recuerda comer antes y llevar un carbohidrato rápido"));
        }
        if (reducedMobility(h)) {
            out.add(Factor.of("movilidad", 0, wheelchair(h)
                    ? "Usas silla de ruedas: elegimos variantes sentadas"
                    : "Movilidad reducida: damos prioridad a las variantes sentadas o con apoyo"));
        }
        if (in.physicalJob()) {
            out.add(Factor.of("trabajo", 0, "Tu trabajo ya es físicamente pesado: sesiones cortas, centradas en movilidad y recuperación"));
        }
        if ("movilidad_general".equals(track) && in.sittingHours() != null && in.sittingHours() >= SITTING_HOURS_TIP) {
            out.add(Factor.of("sentado", 0, "Pasas unas " + in.sittingHours() + " h sentado al día: además de las sesiones, te proponemos pausas activas (OMS 2020)"));
        }
        return out;
    }

    /** Texto extra para las notas de cada sesión (null si no aplica). */
    static String sessionNote(String track, ProfileInsights in) {
        List<String> parts = new ArrayList<>();
        if (in.hypoglycemiaRisk()) {
            parts.add("Usas insulina: come algo 1–2 h antes, lleva un carbohidrato rápido (jugo, dulce) y, si tu médico lo indicó, mide tu glucosa antes y después. Si sientes temblor, sudor frío o mareo, detente y come.");
        }
        if (in.heartRateUnreliable() && "cardiometabolico".equals(track)) {
            parts.add("Con betabloqueadores no te guíes por el pulso: en moderada puedes hablar pero no cantar (esfuerzo 3–5 de 10).");
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    // ── Inicio ──

    /** Consejos del inicio que citan el dato del Perfil 360 o del chequeo. */
    static List<Insight> tips(String track, HealthSummary h, ProfileInsights in) {
        List<Insight> out = new ArrayList<>();
        if (in.hypoglycemiaRisk()) {
            out.add(Insight.of("🍬", "Cuida tu glucosa", "Como usas insulina: come algo antes de entrenar, lleva un carbohidrato rápido y evita entrenar en ayunas.", "warn"));
        }
        if (in.heartRateUnreliable()) {
            out.add(Insight.of("🗣️", "Guíate por la prueba del habla", "Con betabloqueadores tu pulso no refleja el esfuerzo: en moderada puedes hablar, pero no cantar.", "info"));
        }
        if (in.physicalJob()) {
            out.add(Insight.of("🦺", "Tu trabajo ya suma", "Como tu trabajo es físicamente pesado, en días duros haz la versión corta y prioriza movilidad y descanso.", "info"));
        }
        if (in.sittingHours() != null && in.sittingHours() >= SITTING_HOURS_TIP) {
            out.add(Insight.of("⏱️", "Rompe el tiempo sentado", "Pasas unas " + in.sittingHours() + " h sentado al día: levántate 2–3 minutos cada hora y camina o estira (OMS 2020).", "info"));
        }
        if (wheelchair(h)) {
            out.add(Insight.of("♿", "Variantes sentadas", "Tu plan usa ejercicios en silla. Si alguno no se adapta a ti, márcalo con «No me gusta» y lo cambiamos.", "info"));
        } else if (reducedMobility(h)) {
            out.add(Insight.of("🪑", "Con apoyo, siempre", "Priorizamos las variantes sentadas o con apoyo; ten una silla firme o un mesón cerca.", "info"));
        }
        return out;
    }
}
