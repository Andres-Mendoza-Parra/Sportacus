package com.sportacus.backend.common;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Serialización JSON compartida (Jackson 3). Se usa para las columnas JSONB: prescripciones,
 * perfiles de mundo, estado de módulos, widgets. Omite nulos para que el JSONB quede compacto.
 */
@Component
public class Json {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<Object>> LIST = new TypeReference<>() {
    };

    private final JsonMapper mapper = JsonMapper.builder()
            .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.NON_NULL))
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    public <T> T read(String raw, Class<T> type) {
        return raw == null ? null : mapper.readValue(raw, type);
    }

    public <T> T read(String raw, TypeReference<T> type) {
        return raw == null ? null : mapper.readValue(raw, type);
    }

    public Map<String, Object> readMap(String raw) {
        return raw == null || raw.isBlank() ? new java.util.LinkedHashMap<>() : mapper.readValue(raw, MAP);
    }

    public List<Object> readList(String raw) {
        return raw == null || raw.isBlank() ? new java.util.ArrayList<>() : mapper.readValue(raw, LIST);
    }

    public JsonNode tree(String raw) {
        return mapper.readTree(raw == null ? "null" : raw);
    }

    public <T> T convert(Object value, Class<T> type) {
        return mapper.convertValue(value, type);
    }

    public <T> T convert(Object value, TypeReference<T> type) {
        return mapper.convertValue(value, type);
    }

    public JsonMapper mapper() {
        return mapper;
    }
}
