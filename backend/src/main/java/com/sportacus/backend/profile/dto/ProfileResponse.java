package com.sportacus.backend.profile.dto;

import java.util.List;

import com.sportacus.backend.questionnaire.dto.ProfileSuggestionResponse;

/** Respuesta de GET/PATCH /api/profile: datos de la pantalla "Cuenta". */
public record ProfileResponse(
        String name,
        String email,
        Integer age,
        Double weightKg,
        Double heightCm,
        String activeModule,
        List<ModuleItem> modules,
        String country,
        String city,
        String language,
        String memberSince, // fecha ISO (yyyy-MM-dd)
        int profileStrengthPct,
        List<ProfileSuggestionResponse> suggestions) {

    /** status: "activo" | "disponible". */
    public record ModuleItem(String name, String status) {
    }
}
