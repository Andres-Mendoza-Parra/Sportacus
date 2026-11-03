package com.sportacus.backend.user;

import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Estado de onboarding del usuario (columna "user".onboarding_completed_at, ver
 * docs/sportacus_migration_003_onboarding.sql). Sigue el mismo patrón resiliente que
 * {@link UserNameRepository}: si la migración no se ha corrido, se asume que el onboarding
 * ya está completo (no hay forma de bloquear a nadie en un flujo que la BD no soporta).
 */
@Repository
public class UserStatusRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserStatusRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isOnboardingComplete(UUID userId) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT onboarding_completed_at FROM sportacus.\"user\" WHERE id = ?",
                    java.sql.Timestamp.class, userId)
                    .stream().anyMatch(ts -> ts != null);
        } catch (DataAccessException columnMissing) {
            return true; // migración pendiente: no se fuerza onboarding
        }
    }

    public void markOnboardingComplete(UUID userId) {
        try {
            jdbcTemplate.update(
                    "UPDATE sportacus.\"user\" SET onboarding_completed_at = now() WHERE id = ?", userId);
        } catch (DataAccessException columnMissing) {
            // migración pendiente: no hay dónde guardarlo, se ignora
        }
    }
}
