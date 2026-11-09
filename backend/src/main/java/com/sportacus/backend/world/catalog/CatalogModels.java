package com.sportacus.backend.world.catalog;

import java.util.List;
import java.util.Map;

import com.sportacus.backend.world.core.Prescription;

/** Modelos de solo lectura del catálogo (contenido como código cargado en la BD). */
public final class CatalogModels {

    private CatalogModels() {
    }

    public record WorldInfo(String key, String name, String tagline, String description, String icon, String color,
            String status, int sortOrder, List<String> highlights, List<TrackInfo> tracks) {

        public boolean selectable() {
            return "activo".equals(status);
        }
    }

    public record TrackInfo(String worldKey, String key, String name, String description, String icon,
            Map<String, Object> config, int sortOrder) {

        public Object config(String k) {
            return config == null ? null : config.get(k);
        }

        public int configInt(String k, int fallback) {
            Object v = config(k);
            return v instanceof Number n ? n.intValue() : fallback;
        }

        public String configString(String k, String fallback) {
            Object v = config(k);
            return v == null ? fallback : String.valueOf(v);
        }
    }

    public record Contraindication(String tag, String level) {
    }

    public record MovementInfo(String key, String name, String description, List<String> steps, List<String> cues,
            List<String> commonMistakes, List<String> bodyRegions, List<String> muscles, List<String> equipment,
            List<String> environments, String impact, Map<String, Object> media, String sourceKey, String license,
            String author, List<Contraindication> contraindications) {

        @SuppressWarnings("unchecked")
        public String firstImage() {
            Object images = media == null ? null : media.get("images");
            if (images instanceof List<?> l && !l.isEmpty()) return String.valueOf(l.get(0));
            return null;
        }

        public String firstMuscle() {
            return muscles.isEmpty() ? null : muscles.get(0);
        }
    }

    /** Movimiento visto desde un mundo concreto (rol, nivel, patrón y atributos propios del mundo). */
    public record WorldMovement(MovementInfo movement, String worldKey, String role, int level, String pattern,
            Map<String, Object> attrs) {

        public String key() {
            return movement.key();
        }

        public String name() {
            return movement.name();
        }

        public Object attr(String k) {
            return attrs == null ? null : attrs.get(k);
        }

        public boolean attrTrue(String k) {
            return Boolean.TRUE.equals(attr(k));
        }

        public double attrNumber(String k, double fallback) {
            Object v = attr(k);
            return v instanceof Number n ? n.doubleValue() : fallback;
        }

        public String attrString(String k) {
            Object v = attr(k);
            return v == null ? null : String.valueOf(v);
        }
    }

    public record StepInfo(int index, String name, String movementKey, Prescription target,
            Map<String, Object> unlockCriteria) {
    }

    public record ProgressionInfo(String key, String worldKey, String name, String description, String family,
            String icon, int sortOrder, String sourceKey, List<StepInfo> steps) {

        public StepInfo step(int index) {
            int i = Math.max(0, Math.min(index, steps.size() - 1));
            return steps.get(i);
        }

        public int lastIndex() {
            return steps.size() - 1;
        }
    }

    public record AssessmentInfo(String key, String worldKey, String name, String description,
            List<String> instructions, String kind, Integer durationS, String metricKey, String unit, int retestDays,
            Map<String, Object> norms, String safetyNote, String sourceKey, int sortOrder) {
    }

    public record MetricInfo(String key, String worldKey, String name, String unit, String valueType,
            String direction, String description) {
    }

    public record SourceInfo(String key, String name, String url, String license, String attribution) {
    }
}
