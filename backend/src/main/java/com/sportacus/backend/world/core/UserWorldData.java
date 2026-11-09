package com.sportacus.backend.world.core;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.sportacus.backend.common.Answers;

/**
 * Inscripción del usuario en un mundo (tabla user_world).
 *
 * @param profile respuestas del onboarding del mundo
 * @param state   estado vivo del módulo (cargas sugeridas, fase actual, ritmos…), lo gestiona cada mundo
 */
public record UserWorldData(UUID id, UUID userId, String worldKey, String trackKey, int level, Answers profile,
        Map<String, Object> state, boolean active, OffsetDateTime enrolledAt, OffsetDateTime lastActiveAt) {

    public Object stateValue(String key) {
        return state == null ? null : state.get(key);
    }

    public Double stateNumber(String key) {
        Object v = stateValue(key);
        return v instanceof Number n ? n.doubleValue() : null;
    }

    public String stateString(String key) {
        Object v = stateValue(key);
        return v == null ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> stateMap(String key) {
        Object v = stateValue(key);
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : new java.util.LinkedHashMap<>();
    }
}
