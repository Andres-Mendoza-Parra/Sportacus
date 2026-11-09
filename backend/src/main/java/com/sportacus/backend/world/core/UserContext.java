package com.sportacus.backend.world.core;

import java.util.UUID;

import com.sportacus.backend.health.HealthSummary;

/** Todo lo que un mundo necesita saber del usuario, sin depender de otros mundos. */
public record UserContext(UUID userId, String displayName, UserProfileData profile, HealthSummary health) {
}
