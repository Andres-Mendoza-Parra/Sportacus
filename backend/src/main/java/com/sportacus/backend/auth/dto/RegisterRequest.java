package com.sportacus.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta de cuenta. La política de contraseñas, la edad y las aceptaciones se validan en {@code AuthService} con
 * mensajes pensados para la persona.
 *
 * @param birthYear        obligatorio; < 12 años → 400; 12–17 → {@code guardianConsent} obligatorio
 * @param acceptPolicy     obligatorio {@code true}: acepta la Política de tratamiento de datos y los Términos
 * @param acceptHealthData opcional: autorización expresa para tratar datos de salud (dato sensible)
 * @param guardianConsent  obligatorio {@code true} si tiene de 12 a 17 años
 */
public record RegisterRequest(

        @Size(max = 80, message = "El nombre no puede superar 80 caracteres")
        String name,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 254, message = "El correo es demasiado largo")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        String password,

        @NotNull(message = "El año de nacimiento es obligatorio")
        Integer birthYear,

        Boolean acceptPolicy,

        Boolean acceptHealthData,

        Boolean guardianConsent
) {
}
