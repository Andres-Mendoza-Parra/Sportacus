package com.sportacus.backend.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Respuestas de un formulario (id de pregunta → valor) con lectura tolerante a tipos: el frontend
 * puede mandar "3" o 3, una opción suelta o una lista. Se usa en perfil, salud, descubrimiento y
 * onboarding de cada mundo. Los valores ausentes devuelven el valor por defecto.
 */
public record Answers(Map<String, Object> values) {

    public Answers {
        values = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }

    public static Answers empty() {
        return new Answers(Map.of());
    }

    public boolean has(String key) {
        Object v = values.get(key);
        if (v == null) return false;
        if (v instanceof String s) return !s.isBlank();
        if (v instanceof Collection<?> c) return !c.isEmpty();
        return true;
    }

    public String string(String key) {
        Object v = values.get(key);
        if (v == null) return null;
        if (v instanceof Collection<?> c) return c.isEmpty() ? null : String.valueOf(c.iterator().next());
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    public String string(String key, String fallback) {
        String v = string(key);
        return v == null ? fallback : v;
    }

    public Integer integer(String key) {
        Double d = number(key);
        return d == null ? null : (int) Math.round(d);
    }

    public int integer(String key, int fallback) {
        Integer v = integer(key);
        return v == null ? fallback : v;
    }

    public Double number(String key) {
        Object v = values.get(key);
        if (v instanceof Number n) return n.doubleValue();
        String s = string(key);
        if (s == null) return null;
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public List<String> list(String key) {
        Object v = values.get(key);
        List<String> out = new ArrayList<>();
        if (v == null) return out;
        if (v instanceof Collection<?> c) {
            for (Object o : c) if (o != null && !String.valueOf(o).isBlank()) out.add(String.valueOf(o));
        } else if (!String.valueOf(v).isBlank()) {
            out.add(String.valueOf(v));
        }
        return out;
    }

    /** Lista sin la opción "ninguno"/"ninguna" que se usa para responder explícitamente "nada". */
    public List<String> listWithoutNone(String key) {
        List<String> out = list(key);
        out.removeIf(s -> s.equals("ninguno") || s.equals("ninguna") || s.equals("no"));
        return out;
    }

    public boolean yes(String key) {
        String v = string(key);
        return v != null && (v.equals("si") || v.equals("true") || v.equals("sí"));
    }

    public boolean contains(String key, String option) {
        return list(key).contains(option);
    }

    public Map<String, Object> asMap() {
        return new LinkedHashMap<>(values);
    }
}
