package com.sportacus.backend.dashboard.dto;

/** Un eje del gráfico de araña (p. ej. "pecho" -> "Pecho", valor 0-100). */
public record RadarPointResponse(String key, String label, int value) {
}
