package com.sportacus.backend.world.core;

import java.util.UUID;

import com.sportacus.backend.health.HealthSummary;

/**
 * Todo lo que un mundo necesita saber del usuario, sin depender de otros mundos: perfil común, salud y el perfil
 * derivado del Perfil 360 ({@link ProfileInsights}).
 */
public record UserContext(UUID userId, String displayName, UserProfileData profile, HealthSummary health,
        ProfileInsights insights) {

    /** Contexto sin Perfil 360 (pruebas y llamadas que no lo necesitan). */
    public UserContext(UUID userId, String displayName, UserProfileData profile, HealthSummary health) {
        this(userId, displayName, profile, health, ProfileInsights.empty());
    }

    public UserContext {
        if (insights == null) insights = ProfileInsights.empty();
    }
}
