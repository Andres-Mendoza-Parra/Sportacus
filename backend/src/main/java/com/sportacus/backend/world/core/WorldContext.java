package com.sportacus.backend.world.core;

/** Usuario + su inscripción en el mundo sobre el que se está trabajando. */
public record WorldContext(UserContext user, UserWorldData world) {
}
