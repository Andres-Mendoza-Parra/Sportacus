package com.sportacus.backend.world.form;

import java.util.List;
import java.util.Map;

/**
 * Formulario servido por el backend y pintado por el asistente genérico del frontend
 * (perfil, chequeo de salud, descubrimiento y onboarding de cada mundo).
 *
 * @param answers respuestas previas para precargar (id de pregunta → valor)
 */
public record FormSpec(String key, String title, String subtitle, List<Question> questions, Map<String, Object> answers) {

    public FormSpec withAnswers(Map<String, Object> previous) {
        return new FormSpec(key, title, subtitle, questions, previous == null ? Map.of() : previous);
    }

    /**
     * Pregunta. Tipos:
     * <ul>
     * <li>{@code options}: una sola opción ({@code options})</li>
     * <li>{@code multi}: varias opciones ({@code options}, hasta {@code max} si se indica)</li>
     * <li>{@code range}: deslizador entero entre {@code min} y {@code max} con paso {@code step}</li>
     * <li>{@code number}: número libre (con {@code min}/{@code max} como validación)</li>
     * <li>{@code text}: texto corto</li>
     * </ul>
     *
     * @param showIf condición simple de visibilidad: {"question":"q_x","in":["a","b"]}
     */
    public record Question(String id, String prompt, String hint, String type, List<Option> options, Integer min,
            Integer max, Integer step, String unit, boolean required, Map<String, Object> showIf) {
    }

    public record Option(String id, String icon, String text, String sub) {
    }
}
