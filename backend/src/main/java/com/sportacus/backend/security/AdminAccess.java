package com.sportacus.backend.security;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.user.User;

/**
 * Administradores = correos de {@code sportacus.admin-emails} (variable SPORTACUS_ADMIN_EMAILS, separados por comas).
 * Vacío = nadie. El proyecto aún no tiene roles; esto protege los endpoints de /api/admin/**.
 */
@Component
public class AdminAccess {

    private final Set<String> admins;

    public AdminAccess(@Value("${sportacus.admin-emails:}") String adminEmails) {
        this.admins = Arrays.stream(adminEmails == null ? new String[0] : adminEmails.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    public boolean isAdmin(String email) {
        return email != null && admins.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    /** Lanza 403 si la persona no es administradora. */
    public void require(User user) {
        if (user == null || !isAdmin(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo para administración");
        }
    }
}
