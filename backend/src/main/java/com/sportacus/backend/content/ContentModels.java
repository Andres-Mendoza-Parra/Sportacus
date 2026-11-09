package com.sportacus.backend.content;

import java.util.List;
import java.util.Map;

/**
 * Formato de los archivos de contenido (docs/18-CONTENIDO-COMO-CODIGO.md).
 * {@code content/core.json} trae fuentes, vocabulario, métricas globales y mundos; cada
 * {@code content/worlds/<mundo>.json} trae métricas, movimientos, progresiones y tests del mundo.
 */
public final class ContentModels {

    private ContentModels() {
    }

    public record CoreFile(List<Source> sources, List<Tag> tags, List<Metric> metrics, List<World> worlds) {
    }

    public record WorldFile(String world, List<Metric> metrics, List<Movement> movements,
            List<Progression> progressions, List<Assessment> assessments) {
    }

    public record Source(String key, String name, String url, String license, String attribution, String notes) {
    }

    public record Tag(String key, String label, String kind, String bodyZone) {
    }

    public record Metric(String key, String world, String name, String unit, String valueType, String direction,
            String description) {
    }

    public record World(String key, String name, String tagline, String description, String icon, String color,
            String status, Integer sortOrder, List<String> highlights, List<Track> tracks) {
    }

    public record Track(String key, String name, String description, String icon, Map<String, Object> config,
            Integer sortOrder) {
    }

    /**
     * Movimiento. Si trae {@code ref} en lugar de {@code key}, reutiliza un movimiento definido en otro
     * archivo y solo declara cómo se usa en este mundo (role, level, pattern, attrs).
     */
    public record Movement(String key, String ref, String name, String description, List<String> steps,
            List<String> cues, List<String> mistakes, List<String> regions, List<String> muscles,
            List<String> equipment, List<String> environments, String impact, Map<String, Object> media,
            String source, String license, String author, List<Contra> contraindications, String role, Integer level,
            String pattern, Map<String, Object> attrs) {

        public String effectiveKey() {
            return key != null ? key : ref;
        }

        public boolean isDefinition() {
            return key != null;
        }
    }

    public record Contra(String tag, String level) {
    }

    public record Progression(String key, String name, String description, String family, String icon,
            Integer sortOrder, String source, List<Step> steps) {
    }

    public record Step(String name, String movement, Map<String, Object> target, Map<String, Object> unlock) {
    }

    public record Assessment(String key, String name, String description, List<String> instructions, String kind,
            Integer durationS, String metric, String unit, Integer retestDays, Map<String, Object> norms,
            String safetyNote, String source, Integer sortOrder) {
    }
}
