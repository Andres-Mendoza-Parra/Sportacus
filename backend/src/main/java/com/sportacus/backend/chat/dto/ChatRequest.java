package com.sportacus.backend.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** includeProfile = true corresponde al botón "Cargar mis datos a la conversación". */
public record ChatRequest(
        @NotBlank(message = "Escribe una pregunta") @Size(max = 500, message = "La pregunta es demasiado larga")
        String message,
        Boolean includeProfile) {
}
