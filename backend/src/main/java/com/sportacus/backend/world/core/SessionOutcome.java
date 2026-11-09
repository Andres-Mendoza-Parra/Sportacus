package com.sportacus.backend.world.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo que un mundo deduce de una sesión registrada. El núcleo lo aplica: guarda métricas, mueve
 * progresiones, fusiona el estado, otorga logros y, si se pide, regenera el plan.
 */
public record SessionOutcome(List<MetricValue> metrics, Map<String, Integer> progressionSteps,
        Map<String, Object> stateUpdates, List<String> highlights, List<AchievementGrant> achievements,
        boolean regeneratePlan, Integer newLevel) {

    public static Builder builder() {
        return new Builder();
    }

    public static SessionOutcome empty() {
        return builder().build();
    }

    public record MetricValue(String key, double value, Map<String, Object> context) {
        public static MetricValue of(String key, double value) {
            return new MetricValue(key, value, Map.of());
        }
    }

    public record AchievementGrant(String key, String title, String icon) {
    }

    public static final class Builder {
        private final List<MetricValue> metrics = new ArrayList<>();
        private final Map<String, Integer> steps = new LinkedHashMap<>();
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<String> highlights = new ArrayList<>();
        private final List<AchievementGrant> achievements = new ArrayList<>();
        private boolean regenerate;
        private Integer level;

        public Builder metric(String key, double value) {
            metrics.add(MetricValue.of(key, value));
            return this;
        }

        public Builder metric(String key, double value, Map<String, Object> context) {
            metrics.add(new MetricValue(key, value, context));
            return this;
        }

        public Builder step(String progressionKey, int stepIndex) {
            steps.put(progressionKey, stepIndex);
            return this;
        }

        public Builder state(String key, Object value) {
            state.put(key, value);
            return this;
        }

        public Builder highlight(String text) {
            highlights.add(text);
            return this;
        }

        public Builder achievement(String key, String title, String icon) {
            achievements.add(new AchievementGrant(key, title, icon));
            return this;
        }

        public Builder regeneratePlan() {
            regenerate = true;
            return this;
        }

        public Builder level(int value) {
            level = value;
            return this;
        }

        public SessionOutcome build() {
            return new SessionOutcome(metrics, steps, state, highlights, achievements, regenerate, level);
        }
    }
}
