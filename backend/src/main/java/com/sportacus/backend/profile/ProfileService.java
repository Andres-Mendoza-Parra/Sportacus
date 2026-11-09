package com.sportacus.backend.profile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.common.Json;
import com.sportacus.backend.health.HealthService;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.health.SensitiveDataCipher;
import com.sportacus.backend.user.User;
import com.sportacus.backend.user.UserRepository;
import com.sportacus.backend.world.core.UserProfileData;
import com.sportacus.backend.world.enrollment.EnrollmentService;
import com.sportacus.backend.world.enrollment.EnrollmentService.MyWorld;
import com.sportacus.backend.world.form.FormSpec;

/** Cuenta, perfil común, exportación y borrado de datos (se conserva la política de datos de docs/07). */
@Service
public class ProfileService {

    private final ProfileRepository profiles;
    private final HealthService health;
    private final EnrollmentService enrollment;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final SensitiveDataCipher cipher;
    private final Json json;

    public ProfileService(ProfileRepository profiles, HealthService health, EnrollmentService enrollment,
            UserRepository users, PasswordEncoder passwordEncoder, JdbcTemplate jdbc, SensitiveDataCipher cipher,
            Json json) {
        this.profiles = profiles;
        this.health = health;
        this.enrollment = enrollment;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.json = json;
    }

    /** Estado del recibimiento: qué pasos faltan. */
    public record OnboardingStatus(boolean profileDone, boolean healthDone, boolean worldDone) {
    }

    public record AccountView(UUID userId, String name, String email, UserProfileData profile, Integer age, Double bmi,
            HealthSummary health, List<MyWorld> worlds, OnboardingStatus onboarding) {
    }

    public AccountView account(User user) {
        UserProfileData p = profiles.load(user.getId());
        HealthSummary h = health.summary(user.getId());
        List<MyWorld> worlds = enrollment.myWorlds(user);
        return new AccountView(user.getId(), user.visibleName(), user.getEmail(), p, p.age(), p.bmi(), h, worlds,
                new OnboardingStatus(p.completed(), h.screened(), !worlds.isEmpty()));
    }

    public FormSpec form(User user) {
        return ProfileForm.form().withAnswers(profiles.asAnswers(user.getId()));
    }

    public AccountView saveForm(User user, Answers answers) {
        profiles.save(user.getId(), answers);
        return account(user);
    }

    public record PatchRequest(String name, Double weightKg, Double heightCm) {
    }

    @Transactional
    public AccountView patch(User user, PatchRequest req) {
        if (req.name() != null) {
            String name = req.name().trim();
            if (name.isEmpty() || name.length() > 80) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre debe tener entre 1 y 80 caracteres");
            }
            user.setDisplayName(name);
            users.save(user);
        }
        if (req.weightKg() != null && (req.weightKg() < 25 || req.weightKg() > 300)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Peso fuera de rango");
        }
        if (req.heightCm() != null && (req.heightCm() < 100 || req.heightCm() > 230)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estatura fuera de rango");
        }
        if (req.weightKg() != null || req.heightCm() != null) {
            profiles.patchBody(user.getId(), req.weightKg(), req.heightCm());
        }
        return account(user);
    }

    public record PasswordRequest(String currentPassword, String newPassword) {
    }

    public void changePassword(User user, PasswordRequest req) {
        if (req.currentPassword() == null || !passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta");
        }
        if (req.newPassword() == null || req.newPassword().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe tener al menos 6 caracteres");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        users.save(user);
    }

    /** Todos los datos del usuario en un solo JSON (derecho de acceso, Ley 1581). */
    public Map<String, Object> export(User user) {
        UUID id = user.getId();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cuenta", Map.of("email", user.getEmail(), "nombre", user.visibleName(),
                "creada", String.valueOf(user.getCreatedAt())));
        out.put("perfil", profiles.load(id));
        out.put("chequeos_salud", jdbc.query("""
                SELECT answers_encrypted, flags, risk_level, requires_clearance, clearance_confirmed, created_at
                FROM health_screening WHERE user_id = ? ORDER BY created_at
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("respuestas", json.readMap(cipher.decrypt(rs.getString(1))));
            m.put("riesgo", rs.getString(3));
            m.put("requiere_aval", rs.getBoolean(4));
            m.put("aval_confirmado", rs.getBoolean(5));
            m.put("fecha", String.valueOf(rs.getObject(6)));
            return m;
        }, id));
        out.put("condiciones", jdbc.queryForList("SELECT tag_key, kind, severity, status FROM health_condition WHERE user_id = ?", id));
        out.put("mundos", jdbc.queryForList("""
                SELECT world_key, track_key, level, status, profile::text AS perfil, enrolled_at
                FROM user_world WHERE user_id = ?
                """, id));
        out.put("sesiones", jdbc.queryForList("""
                SELECT s.performed_on, s.title, s.duration_min, s.rpe, s.pain_before, s.pain_after, s.pain_next_day,
                       s.distance_m, w.world_key
                FROM session_log s JOIN user_world w ON w.id = s.user_world_id WHERE s.user_id = ? ORDER BY s.performed_on
                """, id));
        out.put("metricas", jdbc.queryForList("""
                SELECT metric_key, value, recorded_at, source FROM metric_entry WHERE user_id = ? ORDER BY recorded_at
                """, id));
        out.put("logros", jdbc.queryForList("SELECT key, title, achieved_at FROM achievement WHERE user_id = ?", id));
        return out;
    }

    /** Borrado total: todas las tablas cuelgan de app_user con ON DELETE CASCADE. */
    @Transactional
    public void delete(User user) {
        jdbc.update("DELETE FROM app_user WHERE id = ?", user.getId());
    }
}
