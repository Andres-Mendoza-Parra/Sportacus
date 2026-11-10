package com.sportacus.backend.world.core;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.sportacus.backend.common.Answers;

/**
 * Últimas respuestas del Perfil 360 (id de pregunta → valor) con la fecha en que se respondió cada una. Incluye las de
 * áreas sensibles solo si el usuario dio su consentimiento (si lo revoca, se borran). Las preguntas marcadas como
 * "prefiero no decir" no aparecen.
 */
public record ProfileFacts(Answers answers, Map<String, LocalDate> answeredOn) {

    public static ProfileFacts empty() {
        return new ProfileFacts(Answers.empty(), Map.of());
    }


    public boolean has(String id) {
        return answers().has(id);
    }

    public String string(String id) {
        return answers().string(id);
    }

    public Double number(String id) {
        return answers().number(id);
    }

    public List<String> list(String id) {
        return answers().list(id);
    }

    public boolean is(String id, String option) {
        return option.equals(string(id));
    }

    public boolean contains(String id, String option) {
        return answers().contains(id, option);
    }

    /** ¿Se respondió hoy? (para el check-in diario). */
    public boolean answeredOn(String id, LocalDate day) {
        return day.equals(answeredOn.get(id));
    }

    public int count() {
        return answers.values().size();
    }
}
