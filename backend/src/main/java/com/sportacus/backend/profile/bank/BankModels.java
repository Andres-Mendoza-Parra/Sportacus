package com.sportacus.backend.profile.bank;

import java.util.List;
import java.util.Map;

import com.sportacus.backend.content.ContentModels.Metric;
import com.sportacus.backend.world.form.FormSpec;

/** Formato de {@code content/questionnaires/*.json} (docs/19 §3). */
public final class BankModels {

    private BankModels() {
    }

    public record AreaFile(Area area, List<Questionnaire> questionnaires, List<Metric> metrics) {
    }

    public record Area(String key, String name, String icon, String description, Integer sortOrder,
            boolean sensitive) {
    }

    public record Questionnaire(String key, String title, String subtitle, Integer minutes, String sourceKey,
            List<BankQuestion> questions) {
    }

    public record BankOption(String id, String icon, String text, String sub) {
    }

    /**
     * @param priority    1–5: cuánto mejora la personalización una sola respuesta
     * @param refreshDays días tras los que vuelve a sugerirse (null = no caduca)
     * @param metric      métrica numérica que se registra además del dato
     * @param profileField columna del perfil común que se actualiza (birth_year, sex, …, country, city)
     * @param cadence     null o "daily" (check-in diario)
     */
    public record BankQuestion(String id, String type, String prompt, String hint, List<BankOption> options,
            Integer min, Integer max, Integer step, String unit, Integer maxChoices, String why, List<String> uses,
            Integer priority, Integer refreshDays, String metric, String profileField, Map<String, Object> showIf,
            String cadence) {

        public int priorityOr3() {
            return priority == null ? 3 : priority;
        }

        public boolean daily() {
            return "daily".equals(cadence);
        }

        /** Pregunta en el formato del asistente genérico del frontend. En el Perfil 360 nada es obligatorio. */
        public FormSpec.Question toForm() {
            List<FormSpec.Option> opts = options == null ? null
                    : options.stream().map(o -> new FormSpec.Option(o.id(), o.icon(), o.text(), o.sub())).toList();
            Integer maxValue = "multi".equals(type) ? maxChoices
                    : "text".equals(type) && max == null ? Integer.valueOf(200) : max;
            return new FormSpec.Question(id, prompt, hint, type, opts, min, maxValue, step, unit, false, showIf);
        }

        public boolean hasOption(String optionId) {
            return options != null && options.stream().anyMatch(o -> o.id().equals(optionId));
        }
    }
}
