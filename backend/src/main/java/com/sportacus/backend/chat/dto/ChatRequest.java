package com.sportacus.backend.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * includeProfile = true corresponde al botón "Cargar mis datos a la conversación".
 * context es el texto ya armado por el frontend (perfil, cuestionarios, rutina actual) cuando
 * includeProfile es true; el backend lo inyecta en el prompt del agente RAG tal cual.
 */
public record ChatRequest(
        @NotBlank(message = "Escribe una pregunta") @Size(max = 500, message = "La pregunta es demasiado larga")
        String message,
        Boolean includeProfile,
        @Size(max = 12000, message = "El contexto es demasiado largo")
        String context) {
}
