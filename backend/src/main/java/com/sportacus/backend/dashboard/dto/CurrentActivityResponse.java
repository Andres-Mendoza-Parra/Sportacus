package com.sportacus.backend.dashboard.dto;

/**
 * Letrero superior (no está en el mockup, se agregó a pedido) que muestra
 * el área / tipo de ejercicio que el usuario está realizando actualmente.
 */
public record CurrentActivityResponse(String area, String subtitle, boolean fromRecentSession) {
}
