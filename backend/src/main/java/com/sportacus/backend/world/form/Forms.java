package com.sportacus.backend.world.form;

import java.util.List;
import java.util.Map;

import com.sportacus.backend.world.form.FormSpec.Option;
import com.sportacus.backend.world.form.FormSpec.Question;

/** Constructores cortos para declarar formularios en código de forma legible. */
public final class Forms {

    private Forms() {
    }

    public static FormSpec form(String key, String title, String subtitle, Question... questions) {
        return new FormSpec(key, title, subtitle, List.of(questions), Map.of());
    }

    public static Option opt(String id, String icon, String text) {
        return new Option(id, icon, text, null);
    }

    public static Option opt(String id, String icon, String text, String sub) {
        return new Option(id, icon, text, sub);
    }

    public static Question single(String id, String prompt, String hint, Option... options) {
        return new Question(id, prompt, hint, "options", List.of(options), null, null, null, null, true, null);
    }

    public static Question multi(String id, String prompt, String hint, Option... options) {
        return new Question(id, prompt, hint, "multi", List.of(options), null, null, null, null, true, null);
    }

    public static Question multi(String id, String prompt, String hint, int maxChoices, Option... options) {
        return new Question(id, prompt, hint, "multi", List.of(options), null, maxChoices, null, null, true, null);
    }

    public static Question range(String id, String prompt, String hint, int min, int max, int step, String unit) {
        return new Question(id, prompt, hint, "range", null, min, max, step, unit, true, null);
    }

    public static Question number(String id, String prompt, String hint, int min, int max, String unit) {
        return new Question(id, prompt, hint, "number", null, min, max, null, unit, true, null);
    }

    public static Question text(String id, String prompt, String hint) {
        return new Question(id, prompt, hint, "text", null, null, 200, null, null, false, null);
    }

    /** Copia de la pregunta marcada como opcional. */
    public static Question optional(Question q) {
        return new Question(q.id(), q.prompt(), q.hint(), q.type(), q.options(), q.min(), q.max(), q.step(), q.unit(),
                false, q.showIf());
    }

    /** Copia de la pregunta que solo se muestra si {@code questionId} tiene alguno de {@code values}. */
    public static Question showIf(Question q, String questionId, String... values) {
        return new Question(q.id(), q.prompt(), q.hint(), q.type(), q.options(), q.min(), q.max(), q.step(), q.unit(),
                q.required(), Map.of("question", questionId, "in", List.of(values)));
    }

    public static Option yes(String text) {
        return opt("si", "✅", text);
    }

    public static Option no(String text) {
        return opt("no", "➖", text);
    }
}
