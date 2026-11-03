package com.sportacus.backend.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Acceso al nombre visible del usuario. Se guarda en dos sitios para funcionar con o sin migración:
 * 1) la columna "user".display_name (ver docs/sportacus_migration_001_display_name.sql), y
 * 2) progress_record (metric_key='display_name', context=nombre), que siempre está disponible.
 * Al leer se prefiere la columna y, si no existe o está vacía, se usa progress_record; si tampoco hay
 * nombre se usa la parte local del correo.
 */
@Repository
public class UserNameRepository {

    private static final String METRIC = "display_name";

    private final JdbcTemplate jdbcTemplate;

    public UserNameRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> find(UUID userId) {
        try {
            Optional<String> column = jdbcTemplate.queryForList(
                    "SELECT display_name FROM sportacus.\"user\" WHERE id = ?", String.class, userId)
                    .stream().filter(n -> n != null && !n.isBlank()).findFirst();
            if (column.isPresent()) {
                return column;
            }
        } catch (DataAccessException columnMissing) {
            // migración pendiente: se usa progress_record
        }
        return jdbcTemplate.queryForList("""
                SELECT context FROM sportacus.progress_record
                WHERE user_id = ? AND metric_key = ? ORDER BY recorded_at DESC, id DESC LIMIT 1
                """, String.class, userId, METRIC).stream().filter(n -> n != null && !n.isBlank()).findFirst();
    }

    public void save(UUID userId, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        String trimmed = name.trim();
        try {
            jdbcTemplate.update("UPDATE sportacus.\"user\" SET display_name = ? WHERE id = ?", trimmed, userId);
        } catch (DataAccessException columnMissing) {
            // migración pendiente: basta con progress_record
        }
        jdbcTemplate.update("DELETE FROM sportacus.progress_record WHERE user_id = ? AND metric_key = ?",
                userId, METRIC);
        jdbcTemplate.update("""
                INSERT INTO sportacus.progress_record (user_id, metric_key, value, recorded_at, context)
                VALUES (?, ?, 1, CURRENT_DATE, ?)
                """, userId, METRIC, trimmed);
    }

    /** Nombre a mostrar: el guardado; si no hay, la parte local del correo. */
    public String displayNameOf(User user) {
        return find(user.getId()).orElseGet(() -> {
            String email = user.getEmail();
            return email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        });
    }
}
