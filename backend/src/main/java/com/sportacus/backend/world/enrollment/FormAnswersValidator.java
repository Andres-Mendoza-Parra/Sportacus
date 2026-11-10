package com.sportacus.backend.world.enrollment;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.world.form.FormSpec;
import com.sportacus.backend.world.form.FormSpec.Question;

/**
 * Valida respuestas contra la definición del formulario (tipo, opciones y rango): un valor que no encaja es un 400
 * con un mensaje claro, en vez de un 500 de la base de datos o un NULL silencioso ({@code p_days: "abc"}).
 * Solo revisa las preguntas presentes: los formularios son parciales (cada quien responde a su ritmo).
 */
public final class FormAnswersValidator {

    private FormAnswersValidator() {
    }

    /** @return las respuestas normalizadas (números como números y opciones como texto) */
    public static Answers validate(FormSpec form, Answers answers) {
        Map<String, Object> out = new LinkedHashMap<>(answers.values());
        for (Question q : form.questions()) {
            if (!answers.values().containsKey(q.id()) || !answers.has(q.id())) continue;
            out.put(q.id(), check(q, answers.values().get(q.id())));
        }
        return new Answers(out);
    }

    static Object check(Question q, Object value) {
        String type = q.type() == null ? "text" : q.type();
        switch (type) {
            case "options" -> {
                if (value instanceof Collection<?> c && c.size() != 1) throw bad(q, "elige una sola opción");
                String v = optionId(value instanceof Collection<?> c ? c.iterator().next() : value);
                if (!hasOption(q, v)) throw bad(q, "opción no válida");
                return v;
            }
            case "multi" -> {
                List<?> items = value instanceof Collection<?> c ? List.copyOf(c) : List.of(value);
                List<String> ids = items.stream().map(FormAnswersValidator::optionId).toList();
                for (String id : ids) if (!hasOption(q, id)) throw bad(q, "opción no válida");
                if (q.max() != null && ids.size() > q.max()) throw bad(q, "elige como máximo " + q.max());
                return ids;
            }
            case "range", "number" -> {
                Double n = new Answers(Map.of(q.id(), value)).number(q.id());
                if (n == null || n.isNaN() || n.isInfinite()) throw bad(q, "debe ser un número");
                if (q.min() != null && n < q.min()) throw bad(q, "el mínimo es " + q.min());
                if (q.max() != null && n > q.max()) throw bad(q, "el máximo es " + q.max());
                return "range".equals(type) ? (Object) (int) Math.round(n) : n;
            }
            default -> {
                String s = String.valueOf(value).trim();
                if (s.length() > 200) throw bad(q, "máximo 200 caracteres");
                return s;
            }
        }
    }

    private static boolean hasOption(Question q, String id) {
        return q.options() != null && q.options().stream().anyMatch(o -> o.id().equals(id));
    }

    /** 30, 30.0 y "30" son la misma opción. */
    private static String optionId(Object v) {
        if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) return String.valueOf(n.longValue());
        return String.valueOf(v).trim();
    }

    private static ResponseStatusException bad(Question q, String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "«" + q.prompt() + "»: " + msg);
    }
}
