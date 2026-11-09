package com.sportacus.backend.world.core;

/**
 * Factor explicable de una puntuación (heredado del motor v2, el activo más valioso de cara a la
 * sustentación): cada punto sumado o restado lleva un texto legible para el usuario.
 */
public record Factor(String key, double points, String text) {

    public static Factor of(String key, double points, String text) {
        return new Factor(key, points, text);
    }

    public boolean positive() {
        return points > 0;
    }
}
