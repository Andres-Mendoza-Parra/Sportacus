package com.sportacus.backend.world.core;

import com.sportacus.backend.common.Answers;

/** Respuestas del test de descubrimiento + lo que ya se sabe del usuario (perfil y salud). */
public record DiscoveryInput(Answers answers, UserContext user) {
}
