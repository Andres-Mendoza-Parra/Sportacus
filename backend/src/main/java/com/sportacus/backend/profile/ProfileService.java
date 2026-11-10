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
    private final Profile360Service profile360;
    private final ProfileAnswerRepository answers;

    public ProfileService(ProfileRepository profiles, HealthService health, EnrollmentService enrollment,
            UserRepository users, PasswordEncoder passwordEncoder, JdbcTemplate jdbc, SensitiveDataCipher cipher,
            Json json, Profile360Service profile360, ProfileAnswerRepository answers) {
        this.profile360 = profile360;
        this.answers = answers;
        this.profiles = profiles;
        this.health = health;
        this.enrollment = enrollment;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.json = json;
    }

    public record AccountView(UUID userId, String name, String email, UserProfileData profile, Integer age, Double bmi,
            HealthSummary health, List<MyWorld> worlds, Profile360Service.Completeness completeness,
            String activeWorld) {
    }

    public AccountView account(User user) {
        UserProfileData p = profiles.load(user.getId());
        HealthSummary h = health.summary(user.getId());
        List<MyWorld> worlds = enrollment.myWorlds(user);
        String active = worlds.stream().filter(MyWorld::active).map(MyWorld::key).findFirst().orElse("base");
        return new AccountView(user.getId(), user.visibleName(), user.getEmail(), p, p.age(), p.bmi(), h, worlds,
                profile360.completeness(user.getId()), active);
    }

    public FormSpec form(User user) {
        return ProfileForm.form().withAnswers(profiles.asAnswers(user.getId()));
    }

    @Transactional
    public AccountView saveForm(User user, Answers answers) {
        // Validado contra la definición: "abc" en días o una opción inexistente es 400, no un NULL silencioso.
        Answers checked = com.sportacus.backend.world.enrollment.FormAnswersValidator.validate(ProfileForm.form(),
                answers == null ? Answers.empty() : answers);
        profiles.save(user.getId(), checked);
        profile360.recordProfileForm(user.getId(), checked);
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
            Map<String, Object> body = new LinkedHashMap<>();
            if (req.weightKg() != null) body.put("p_weight", req.weightKg());
            if (req.heightCm() != null) body.put("p_height", req.heightCm());
            profile360.recordProfileForm(user.getId(), new Answers(body));
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
        out.put("progresiones", jdbc.queryForList("""
                SELECT w.world_key, p.progression_key, p.current_step, p.best_step, p.updated_at
                FROM user_progression p JOIN user_world w ON w.id = p.user_world_id WHERE w.user_id = ?
                ORDER BY w.world_key, p.progression_key
                """, id));
        out.put("metas", jdbc.queryForList("""
                SELECT w.world_key, g.goal_key, g.label, g.target::text AS objetivo, g.target_date, g.status,
                       g.achieved_by, g.created_at, g.achieved_at
                FROM user_world_goal g JOIN user_world w ON w.id = g.user_world_id WHERE w.user_id = ?
                ORDER BY g.created_at
                """, id));
        out.put("planes", plans(id));
        out.put("sesiones", jdbc.queryForList("""
                SELECT s.id, s.performed_on, s.title, s.duration_min, s.rpe, s.pain_before, s.pain_after,
                       s.pain_next_day, s.mood_before, s.mood_after, s.distance_m, s.completed, s.notes, w.world_key,
                       s.created_at
                FROM session_log s JOIN user_world w ON w.id = s.user_world_id WHERE s.user_id = ? ORDER BY s.performed_on
                """, id));
        out.put("series_registradas", jdbc.queryForList("""
                SELECT b.session_log_id, b.movement_key, b.set_index, b.reps, b.load_kg, b.rir, b.hold_s, b.duration_s,
                       b.distance_m, b.pain, b.completed
                FROM block_log b JOIN session_log s ON s.id = b.session_log_id WHERE s.user_id = ?
                ORDER BY s.performed_on, b.session_log_id, b.set_index
                """, id));
        out.put("metricas", jdbc.queryForList("""
                SELECT metric_key, value, recorded_at, source FROM metric_entry WHERE user_id = ? ORDER BY recorded_at
                """, id));
        out.put("perfil_360", answers.export(id));
        out.put("estados_preguntas", jdbc.queryForList("""
                SELECT question_key, status, until, updated_at FROM profile_question_state WHERE user_id = ?
                ORDER BY question_key
                """, id));
        out.put("consentimientos", answers.consents(id));
        out.put("registro_consentimientos", optionalTable("consent_log", """
                SELECT document, version, granted, source, created_at FROM consent_log WHERE user_id = ?
                ORDER BY created_at
                """, id));
        out.put("favoritos", jdbc.queryForList(
                "SELECT movement_key, created_at FROM favorite_movement WHERE user_id = ? ORDER BY created_at", id));
        out.put("reacciones_ejercicios", jdbc.queryForList("""
                SELECT world_key, movement_key, reaction, created_at FROM movement_feedback WHERE user_id = ?
                ORDER BY created_at
                """, id));
        out.put("descubrimientos", jdbc.queryForList("""
                SELECT answers::text AS respuestas, ranking::text AS ranking, chosen_world_key, created_at
                FROM world_discovery WHERE user_id = ? ORDER BY created_at
                """, id));
        out.put("racha", jdbc.queryForList(
                "SELECT current_days, longest_days, last_active_date FROM user_streak WHERE user_id = ?", id));
        out.put("logros", jdbc.queryForList(
                "SELECT key, world_key, title, achieved_at FROM achievement WHERE user_id = ? ORDER BY achieved_at", id));
        out.put("eventos_de_uso", optionalTable("event_log", """
                SELECT name, props::text AS propiedades, source, occurred_at FROM event_log WHERE user_id = ?
                ORDER BY occurred_at
                """, id));
        return out;
    }

    /** Planes (activos y archivados) con sus sesiones y bloques. */
    private List<Map<String, Object>> plans(UUID userId) {
        List<Map<String, Object>> plans = jdbc.queryForList("""
                SELECT p.id, w.world_key, p.track_key, p.title, p.summary, p.status, p.weeks, p.starts_on, p.created_at
                FROM plan p JOIN user_world w ON w.id = p.user_world_id WHERE w.user_id = ? ORDER BY p.created_at
                """, userId);
        Map<Object, List<Map<String, Object>>> sessions = new LinkedHashMap<>();
        jdbc.queryForList("""
                SELECT s.plan_id, s.id, s.week_index, s.day_index, s.weekday, s.title, s.kind, s.est_minutes
                FROM plan_session s JOIN plan p ON p.id = s.plan_id JOIN user_world w ON w.id = p.user_world_id
                WHERE w.user_id = ? ORDER BY s.week_index, s.day_index
                """, userId).forEach(s -> sessions.computeIfAbsent(s.remove("plan_id"), k -> new java.util.ArrayList<>()).add(s));
        Map<Object, List<Map<String, Object>>> blocks = new LinkedHashMap<>();
        jdbc.queryForList("""
                SELECT b.plan_session_id, b.order_index, b.block_type, b.movement_key, b.title,
                       b.prescription::text AS prescripcion
                FROM plan_block b JOIN plan_session s ON s.id = b.plan_session_id JOIN plan p ON p.id = s.plan_id
                JOIN user_world w ON w.id = p.user_world_id WHERE w.user_id = ? ORDER BY b.order_index
                """, userId).forEach(b -> blocks.computeIfAbsent(b.remove("plan_session_id"), k -> new java.util.ArrayList<>()).add(b));
        for (Map<String, Object> p : plans) {
            List<Map<String, Object>> ss = sessions.getOrDefault(p.get("id"), List.of());
            for (Map<String, Object> s : ss) s.put("bloques", blocks.getOrDefault(s.get("id"), List.of()));
            p.put("sesiones", ss);
        }
        return plans;
    }

    /** Tablas que pueden no existir en una base a medio migrar: la exportación nunca falla por ellas. */
    private List<Map<String, Object>> optionalTable(String table, String sql, UUID userId) {
        Boolean exists = jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table);
        if (!Boolean.TRUE.equals(exists)) return List.of();
        return jdbc.queryForList(sql, userId);
    }

    /** Borrado total: todas las tablas cuelgan de app_user con ON DELETE CASCADE. */
    @Transactional
    public void delete(User user) {
        jdbc.update("DELETE FROM app_user WHERE id = ?", user.getId());
    }
}
