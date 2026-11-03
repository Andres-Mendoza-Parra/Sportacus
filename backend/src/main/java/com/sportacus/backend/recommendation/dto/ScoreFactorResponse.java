package com.sportacus.backend.recommendation.dto;

/** Un factor del score de afinidad: cuántos puntos aportó (o restó) y cómo se llama para el usuario. */
public record ScoreFactorResponse(String key, String label, int points) {
}
