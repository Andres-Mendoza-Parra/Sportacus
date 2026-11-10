package com.sportacus.backend.world.plan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.core.UserProfileData;

/**
 * "Huella" de los datos con que se generó un plan (docs/22). Si cualquier categoría cambia, el plan se regenera solo
 * al consultarlo y el usuario ve por qué. Se excluye lo diario (check-in: se aplica al ver, no se guarda en el plan).
 */
public final class PlanInputs {

    private PlanInputs() {
    }

    /** Motivo legible por categoría que cambió. */
    static final Map<String, String> REASONS = Map.of(
            "salud", "actualizaste tu chequeo de salud",
            "perfil", "cambiaste tus datos (días, tiempo, lugar, equipo o datos físicos)",
            "perfil360", "nos contaste más sobre ti en tu Perfil 360",
            "mundo", "cambiaste tus respuestas o tu nivel en este mundo",
            "semana", "empezó una nueva semana",
            "contenido", "mejoramos los ejercicios y las reglas de seguridad");

    /**
     * @param worldProfile respuestas del formulario del mundo
     * @param steps        escalones de progresión actuales
     * @param weekKey      semana ISO (solo para mundos cuyo plan avanza por semanas; null si no)
     * @param contentHash  versión del contenido cargado
     */
    public static Map<String, String> parts(UserProfileData p, HealthSummary h, ProfileInsights in, String trackKey,
            int level, Map<String, Object> worldProfile, Map<String, Integer> steps, String weekKey,
            String contentHash, String engineVersion) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("salud", hash(List.of(h.screened(), h.riskLevel(), new TreeSet<>(h.flags()), h.requiresClearance(),
                h.clearanceConfirmed(), h.redFlags(), new TreeSet<>(h.activeTags()),
                h.conditions().stream().map(c -> c.tagKey() + ":" + c.severity()).sorted().toList())));
        Double bmi = p.bmi();
        out.put("perfil", hash(Arrays.asList(p.age(), p.sex(), bmi == null ? null : (int) Math.floor(bmi),
                p.daysPerWeek(), p.minutesPerSession(), p.preferredDays(), sorted(p.environments()),
                sorted(p.equipment()))));
        Map<String, Object> facts = new TreeMap<>();
        in.facts().answers().values().forEach((k, v) -> {
            if (!k.startsWith("ck_") && !k.startsWith("p_") && !k.startsWith("s_")) facts.put(k, v);
        });
        out.put("perfil360", hash(facts));
        out.put("mundo", hash(Arrays.asList(trackKey, level, new TreeMap<>(worldProfile == null ? Map.of() : worldProfile),
                new TreeMap<>(steps == null ? Map.of() : steps))));
        out.put("semana", weekKey == null ? "-" : weekKey);
        out.put("contenido", (contentHash == null ? "-" : contentHash) + "|" + engineVersion);
        return out;
    }

    public static String total(Map<String, String> parts) {
        return hash(new TreeMap<>(parts));
    }

    /** Motivo del cambio entre la huella anterior y la actual (null si no hay anterior o no cambió nada visible). */
    public static String reason(Map<String, Object> previous, Map<String, String> current) {
        if (previous == null || previous.isEmpty()) return null;
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, String> e : current.entrySet()) {
            Object old = previous.get(e.getKey());
            if (old != null && !old.equals(e.getValue()) && REASONS.containsKey(e.getKey())) {
                changed.add(REASONS.get(e.getKey()));
            }
        }
        if (changed.isEmpty()) return null;
        return "Actualizamos tu plan porque " + joinEs(changed) + ".";
    }

    private static List<String> sorted(List<String> l) {
        return l == null ? List.of() : new ArrayList<>(new TreeSet<>(l));
    }

    private static String joinEs(List<String> items) {
        if (items.size() == 1) return items.get(0);
        return String.join(", ", items.subList(0, items.size() - 1)) + " y " + items.get(items.size() - 1);
    }

    static String hash(Object value) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] h = d.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Arrays {
        private Arrays() {
        }

        static List<Object> asList(Object... values) {
            return new ArrayList<>(java.util.Arrays.asList(values));
        }
    }
}
