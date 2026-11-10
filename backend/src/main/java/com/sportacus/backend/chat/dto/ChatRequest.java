package com.sportacus.backend.chat.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * includeProfile = true corresponde al interruptor "usar mis datos": el backend arma el contexto del usuario.
 * context es texto adicional opcional del frontend. history son los últimos turnos de la conversación (el historial
 * vive en el teléfono; el backend no lo guarda) para que el asistente entienda preguntas de seguimiento.
 */
public record ChatRequest(
        @NotBlank(message = "Escribe una pregunta") @Size(max = 500, message = "La pregunta es demasiado larga")
        String message,
        Boolean includeProfile,
        @Size(max = 12000, message = "El contexto es demasiado largo")
        String context,
        @Valid @Size(max = 20, message = "Demasiados mensajes en el historial")
        List<Turn> history) {

    public ChatRequest(String message, Boolean includeProfile, String context) {
        this(message, includeProfile, context, null);
    }

    /** Un turno previo: role = user | assistant. */
    public record Turn(
            @Pattern(regexp = "user|assistant", message = "Rol de historial inválido") String role,
            @NotBlank @Size(max = 4000, message = "Un mensaje del historial es demasiado largo") String text) {
    }
}
