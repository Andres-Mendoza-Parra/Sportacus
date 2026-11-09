package com.sportacus.backend.world.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Índice de los mundos implementados (todos los beans {@link WorldModule}). */
@Component
public class WorldRegistry {

    private final Map<String, WorldModule> modules = new LinkedHashMap<>();

    public WorldRegistry(List<WorldModule> all) {
        for (WorldModule m : all) {
            if (modules.put(m.key(), m) != null) {
                throw new IllegalStateException("Mundo duplicado: " + m.key());
            }
        }
    }

    public Optional<WorldModule> find(String key) {
        return Optional.ofNullable(modules.get(key));
    }

    public WorldModule require(String key) {
        return find(key).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "El mundo '" + key + "' no está disponible todavía"));
    }

    public Collection<WorldModule> all() {
        return modules.values();
    }

    public boolean isImplemented(String key) {
        return modules.containsKey(key);
    }
}
