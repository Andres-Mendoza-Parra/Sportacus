package com.sportacus.backend.health;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.world.form.FormSpec;

/**
 * Valida un chequeo de salud contra su formulario (auditoría A3: {@code {}} ya no cuenta como chequeo hecho).
 * <ul>
 * <li>Toda pregunta obligatoria y visible debe tener respuesta.</li>
 * <li>Las opciones deben existir; en las múltiples, "ninguna/ninguno" no se combina con otras.</li>
 * <li>Se descartan claves ajenas al formulario y respuestas de preguntas ocultas por su condición.</li>
 * </ul>
 * Lógica pura: lanza {@link Invalid} con un mensaje para la persona.
 */
public final class ScreeningValidator {

    private static final Set<String> NONE = Set.of("ninguna", "ninguno");

    private ScreeningValidator() {
    }

    public static final class Invalid extends RuntimeException {
        public Invalid(String message) {
            super(message);
        }
    }

    public static Map<String, Object> clean(FormSpec form, Map<String, Object> raw) {
        Answers a = new Answers(raw);
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (FormSpec.Question q : form.questions()) {
            if (!visible(q, a)) continue;
            if (!a.has(q.id())) {
                if (q.required()) missing.add(q.prompt());
                continue;
            }
            Set<String> valid = q.options() == null ? Set.of()
                    : q.options().stream().map(FormSpec.Option::id).collect(Collectors.toSet());
            switch (q.type()) {
                case "options" -> {
                    if (a.list(q.id()).size() != 1) throw new Invalid("\"" + q.prompt() + "\": elige una sola opción");
                    String v = a.string(q.id());
                    if (!valid.contains(v)) throw new Invalid("\"" + q.prompt() + "\": opción no válida");
                    out.put(q.id(), v);
                }
                case "multi" -> {
                    List<String> vs = a.list(q.id()).stream().distinct().toList();
                    for (String v : vs) {
                        if (!valid.contains(v)) throw new Invalid("\"" + q.prompt() + "\": opción no válida");
                    }
                    if (vs.size() > 1 && vs.stream().anyMatch(NONE::contains)) {
                        throw new Invalid("\"" + q.prompt() + "\": \"Ninguna\" no se combina con otras opciones");
                    }
                    if (q.max() != null && vs.size() > q.max()) {
                        throw new Invalid("\"" + q.prompt() + "\": demasiadas opciones");
                    }
                    out.put(q.id(), vs);
                }
                default -> out.put(q.id(), a.values().get(q.id()));
            }
        }
        if (!missing.isEmpty()) {
            throw new Invalid(missing.size() == 1 ? "Falta responder: " + missing.get(0)
                    : "Faltan " + missing.size() + " respuestas del chequeo. Primera pendiente: " + missing.get(0));
        }
        return out;
    }

    static boolean visible(FormSpec.Question q, Answers a) {
        if (q.showIf() == null) return true;
        Object target = q.showIf().get("question");
        Object in = q.showIf().get("in");
        if (!(target instanceof String t) || !(in instanceof List<?> values)) return true;
        for (String v : a.list(t)) {
            if (values.stream().anyMatch(x -> String.valueOf(x).equals(v))) return true;
        }
        return false;
    }
}
