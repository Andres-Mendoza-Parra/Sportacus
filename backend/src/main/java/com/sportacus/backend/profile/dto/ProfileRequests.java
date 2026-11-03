package com.sportacus.backend.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpos de las peticiones de /api/profile. */
public final class ProfileRequests {

    /** Todos los campos son opcionales: solo se actualizan los enviados. */
    public record UpdateProfileRequest(
            @Size(max = 80, message = "El nombre no puede superar 80 caracteres") String name,
            @Size(max = 60, message = "El país no puede superar 60 caracteres") String country,
            @Size(max = 80, message = "La ciudad no puede superar 80 caracteres") String city,
            @Size(max = 5, message = "El idioma no es válido") String language) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "La contraseña actual es obligatoria") String currentPassword,
            @NotBlank(message = "La nueva contraseña es obligatoria")
            @Size(min = 6, message = "La nueva contraseña debe tener al menos 6 caracteres") String newPassword) {
    }

    public record DeleteAccountRequest(@NotBlank(message = "La contraseña es obligatoria") String password) {
    }

    private ProfileRequests() {
    }
}
