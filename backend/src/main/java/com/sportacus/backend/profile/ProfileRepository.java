package com.sportacus.backend.profile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.common.JdbcArrays;
import com.sportacus.backend.world.core.UserProfileData;

/** Tabla user_profile (perfil común). */
@Repository
public class ProfileRepository {

    private final JdbcTemplate jdbc;

    public ProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UserProfileData load(UUID userId) {
        List<UserProfileData> rows = jdbc.query("""
                SELECT birth_year, sex, weight_kg, height_cm, activity_level, days_per_week, minutes_per_session,
                       preferred_days, environments, equipment, completed_at
                FROM user_profile WHERE user_id = ?
                """, (rs, i) -> new UserProfileData(
                rs.getObject("birth_year") == null ? null : rs.getInt("birth_year"),
                rs.getString("sex"),
                rs.getObject("weight_kg") == null ? null : rs.getDouble("weight_kg"),
                rs.getObject("height_cm") == null ? null : rs.getDouble("height_cm"),
                rs.getObject("activity_level") == null ? null : rs.getInt("activity_level"),
                rs.getObject("days_per_week") == null ? null : rs.getInt("days_per_week"),
                rs.getObject("minutes_per_session") == null ? null : rs.getInt("minutes_per_session"),
                JdbcArrays.ints(rs, "preferred_days"),
                JdbcArrays.strings(rs, "environments"),
                JdbcArrays.strings(rs, "equipment"),
                rs.getTimestamp("completed_at") != null), userId);
        return rows.isEmpty() ? UserProfileData.empty() : rows.get(0);
    }

    /** Guarda las respuestas del formulario de perfil (upsert completo). */
    public void save(UUID userId, Answers a) {
        List<Integer> days = new ArrayList<>();
        for (String d : a.list("p_preferred_days")) {
            try {
                days.add(Integer.parseInt(d));
            } catch (NumberFormatException ignored) {
                // opción desconocida: se ignora
            }
        }
        List<String> equipment = a.listWithoutNone("p_equipment");
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO user_profile (user_id, birth_year, sex, weight_kg, height_cm, activity_level,
                        days_per_week, minutes_per_session, preferred_days, environments, equipment, completed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                    ON CONFLICT (user_id) DO UPDATE SET
                        birth_year = EXCLUDED.birth_year, sex = EXCLUDED.sex, weight_kg = EXCLUDED.weight_kg,
                        height_cm = EXCLUDED.height_cm, activity_level = EXCLUDED.activity_level,
                        days_per_week = EXCLUDED.days_per_week, minutes_per_session = EXCLUDED.minutes_per_session,
                        preferred_days = EXCLUDED.preferred_days, environments = EXCLUDED.environments,
                        equipment = EXCLUDED.equipment, completed_at = now()
                    """);
            ps.setObject(1, userId);
            ps.setObject(2, a.integer("p_birth_year"));
            ps.setString(3, a.string("p_sex"));
            ps.setObject(4, a.number("p_weight"));
            ps.setObject(5, a.number("p_height"));
            ps.setObject(6, a.integer("p_activity"));
            ps.setObject(7, a.integer("p_days"));
            ps.setObject(8, a.integer("p_minutes"));
            ps.setArray(9, JdbcArrays.smallintArray(con, days));
            ps.setArray(10, JdbcArrays.textArray(con, a.list("p_environments")));
            ps.setArray(11, JdbcArrays.textArray(con, equipment));
            return ps;
        });
    }

    /** Respuestas actuales en formato de formulario, para precargar el asistente. */
    public Map<String, Object> asAnswers(UUID userId) {
        UserProfileData p = load(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        if (p.birthYear() != null) m.put("p_birth_year", p.birthYear());
        if (p.sex() != null) m.put("p_sex", p.sex());
        if (p.weightKg() != null) m.put("p_weight", p.weightKg());
        if (p.heightCm() != null) m.put("p_height", p.heightCm());
        if (p.activityLevel() != null) m.put("p_activity", String.valueOf(p.activityLevel()));
        if (p.daysPerWeek() != null) m.put("p_days", p.daysPerWeek());
        if (p.minutesPerSession() != null) m.put("p_minutes", String.valueOf(p.minutesPerSession()));
        if (!p.preferredDays().isEmpty()) m.put("p_preferred_days", p.preferredDays().stream().map(String::valueOf).toList());
        if (!p.environments().isEmpty()) m.put("p_environments", p.environments());
        if (p.completed()) m.put("p_equipment", p.equipment().isEmpty() ? List.of("ninguno") : p.equipment());
        return m;
    }

    /** Actualización parcial desde la pantalla de cuenta. */
    public void patchBody(UUID userId, Double weightKg, Double heightCm) {
        jdbc.update("""
                INSERT INTO user_profile (user_id, weight_kg, height_cm) VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                    weight_kg = COALESCE(EXCLUDED.weight_kg, user_profile.weight_kg),
                    height_cm = COALESCE(EXCLUDED.height_cm, user_profile.height_cm)
                """, userId, weightKg, heightCm);
    }
}
