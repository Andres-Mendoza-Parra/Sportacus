package com.sportacus.backend.chat.dto;

import java.util.List;

/**
 * @param sources fragmentos de conocimiento que se le dieron al modelo para responder (para mostrar "Basado en…")
 * @param model   modelo local que generó la respuesta
 */
public record ChatResponse(String reply, boolean personalized, List<Source> sources, String model) {

    public record Source(String type, String title, String reference) {
    }
}
