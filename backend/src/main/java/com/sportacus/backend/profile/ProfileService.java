package com.sportacus.backend.profile;

import java.sql.Array;
import java.sql.Date;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.profile.dto.ProfileRequests.UpdateProfileRequest;
import com.sportacus.backend.profile.dto.ProfileResponse;
import com.sportacus.backend.profile.dto.ProfileResponse.ModuleItem;
import com.sportacus.backend.questionnaire.QuestionnaireService;
import com.sportacus.backend.user.User;
import com.sportacus.backend.user.UserNameRepository;

@Service
public class ProfileService {

    private static final int MAX_AVAILABLE_MODULES = 3;
    private static final Set<String> LANGUAGES = Set.of("es", "en", "pt", "fr");

    private final JdbcTemplate jdbcTemplate;
    private final UserNameRepository userNameRepository;
    private final QuestionnaireService questionnaireService;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(JdbcTemplate jdbcTemplate, UserNameRepository userNameRepository,
            QuestionnaireService questionnaireService, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.userNameRepository = userNameRepository;
        this.questionnaireService = questionnaireService;
        this.passwordEncoder = passwordEncoder;
    }

    public ProfileResponse getProfile(User user) {
        UUID userId = user.getId();
        Integer age = null;
        Double weight = null;
        Double height = null;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT birth_date, weight_kg, height_cm FROM sportacus.user_profile WHERE user_id = ?", userId);
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            if (row.get("birth_date") != null) {
                age = Period.between(((Date) row.get("birth_date")).toLocalDate(), LocalDate.now()).getYears();
            }
            if (row.get("weight_kg") instanceof Number n) {
                weight = n.doubleValue();
            }
            if (row.get("height_cm") instanceof Number n) {
                height = n.doubleValue();
            }
        }

        String activeModule = jdbcTemplate.queryForList("""
                SELECT a.name FROM sportacus.route_recommendation r
                JOIN sportacus.activity a ON a.id = r.activity_id
                WHERE r.user_id = ? ORDER BY r.created_at DESC LIMIT 1
                """, String.class, userId).stream().findFirst().orElse(null);

        List<ModuleItem> modules = new ArrayList<>();
        if (activeModule != null) {
            modules.add(new ModuleItem(activeModule, "activo"));
        }
        String exclude = activeModule == null ? "" : activeModule;
        jdbcTemplate.queryForList(
                "SELECT name FROM sportacus.activity WHERE is_active AND name <> ? ORDER BY name LIMIT ?",
                String.class, exclude, MAX_AVAILABLE_MODULES)
                .forEach(name -> modules.add(new ModuleItem(name, "disponible")));

        // Datos de cuenta: se leen frescos de BD (el principal del token puede estar desactualizado).
        Map<String, Object> account = jdbcTemplate.queryForList(
                "SELECT country, city, language, created_at FROM sportacus.\"user\" WHERE id = ?", userId).get(0);
        String memberSince = account.get("created_at") instanceof java.sql.Timestamp ts
                ? ts.toLocalDateTime().toLocalDate().toString()
                : (account.get("created_at") instanceof java.time.OffsetDateTime odt ? odt.toLocalDate().toString() : null);

        return new ProfileResponse(userNameRepository.displayNameOf(user), user.getEmail(),
                age, weight, height, activeModule, modules,
                (String) account.get("country"), (String) account.get("city"),
                account.get("language") == null ? null : ((String) account.get("language")).trim(), memberSince,
                questionnaireService.profileStrengthPct(userId), questionnaireService.suggestions(userId));
    }

    /** Actualiza solo los campos enviados (null = sin cambios; cadena vacía en país/ciudad = borrar). */
    public ProfileResponse updateProfile(User user, UpdateProfileRequest request) {
        UUID userId = user.getId();
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre no puede estar vacío");
            }
            userNameRepository.save(userId, request.name());
        }
        if (request.language() != null) {
            String language = request.language().trim().toLowerCase();
            if (!LANGUAGES.contains(language)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Idioma no soportado. Usa: " + String.join(", ", LANGUAGES));
            }
            jdbcTemplate.update("UPDATE sportacus.\"user\" SET language = ?, updated_at = NOW() WHERE id = ?",
                    language, userId);
        }
        if (request.country() != null) {
            jdbcTemplate.update("UPDATE sportacus.\"user\" SET country = ?, updated_at = NOW() WHERE id = ?",
                    emptyToNull(request.country()), userId);
        }
        if (request.city() != null) {
            jdbcTemplate.update("UPDATE sportacus.\"user\" SET city = ?, updated_at = NOW() WHERE id = ?",
                    emptyToNull(request.city()), userId);
        }
        return getProfile(user);
    }

    public void changePassword(User user, String currentPassword, String newPassword) {
        String hash = currentHash(user.getId());
        if (!passwordEncoder.matches(currentPassword, hash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta");
        }
        jdbcTemplate.update("UPDATE sportacus.\"user\" SET password_hash = ?, updated_at = NOW() WHERE id = ?",
                passwordEncoder.encode(newPassword), user.getId());
    }

    /** Elimina la cuenta y, por ON DELETE CASCADE, todos sus datos. */
    @Transactional
    public void deleteAccount(User user, String password) {
        if (!passwordEncoder.matches(password, currentHash(user.getId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña no es correcta");
        }
        jdbcTemplate.update("DELETE FROM sportacus.\"user\" WHERE id = ?", user.getId());
    }

    /** Todos los datos del usuario (sin la contraseña) para descargarlos. */
    public Map<String, Object> exportData(User user) {
        UUID userId = user.getId();
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", java.time.OffsetDateTime.now().toString());
        export.put("account", getProfile(user));
        export.put("userProfile", rows("SELECT * FROM sportacus.user_profile WHERE user_id = ?", userId));
        export.put("goals", rows("SELECT * FROM sportacus.user_goal WHERE user_id = ? ORDER BY created_at", userId));
        export.put("nutrition", rows("SELECT * FROM sportacus.nutrition_profile WHERE user_id = ?", userId));
        export.put("injuries", rows("SELECT * FROM sportacus.injury WHERE user_id = ?", userId));
        export.put("conditions", rows("SELECT * FROM sportacus.condition WHERE user_id = ?", userId));
        export.put("resources", rows("SELECT * FROM sportacus.user_resources WHERE user_id = ?", userId));
        export.put("equipment", rows("""
                SELECT eq.name, eq.category FROM sportacus.user_equipment ue
                JOIN sportacus.user_resources ur ON ur.id = ue.user_resources_id
                JOIN sportacus.equipment eq ON eq.id = ue.equipment_id WHERE ur.user_id = ?
                """, userId));
        export.put("bodyMeasurements", rows("SELECT * FROM sportacus.body_measurement WHERE user_id = ? ORDER BY measured_at", userId));
        export.put("currentRoute", rows("""
                SELECT a.name AS activity, rr.score, rr.rationale, rr.created_at,
                       (SELECT COALESCE(json_agg(e.name ORDER BY er.order_index)::text, '[]')
                        FROM sportacus.exercise_recommendation er
                        JOIN sportacus.exercise e ON e.id = er.exercise_id
                        WHERE er.route_recommendation_id = rr.id) AS exercises
                FROM sportacus.route_recommendation rr JOIN sportacus.activity a ON a.id = rr.activity_id
                WHERE rr.user_id = ? ORDER BY rr.created_at DESC LIMIT 1
                """, userId));
        export.put("workoutSessions", rows("SELECT * FROM sportacus.workout_session WHERE user_id = ? ORDER BY session_date", userId));
        export.put("records", rows("""
                SELECT metric_key, value, recorded_at, context FROM sportacus.progress_record
                WHERE user_id = ? ORDER BY recorded_at
                """, userId));
        return export;
    }

    private String currentHash(UUID userId) {
        return jdbcTemplate.queryForObject("SELECT password_hash FROM sportacus.\"user\" WHERE id = ?",
                String.class, userId);
    }

    private List<Map<String, Object>> rows(String sql, Object... params) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, params)) {
            Map<String, Object> clean = new LinkedHashMap<>();
            row.forEach((k, v) -> clean.put(k, toPlain(v)));
            result.add(clean);
        }
        return result;
    }

    /** Convierte tipos de JDBC (arrays de PostgreSQL, fechas, UUID) a valores serializables. */
    private Object toPlain(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof Array array) {
            try {
                List<Object> items = new ArrayList<>();
                for (Object item : (Object[]) array.getArray()) {
                    items.add(toPlain(item));
                }
                return items;
            } catch (Exception e) {
                return null;
            }
        }
        if (value instanceof Collection<?> c) {
            return c.stream().map(this::toPlain).toList();
        }
        return value.toString();
    }

    private String emptyToNull(String value) {
        return value.isBlank() ? null : value.trim();
    }
}
