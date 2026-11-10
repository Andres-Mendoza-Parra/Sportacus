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

    /** Columnas editables del perfil común (id de pregunta → columna). */
    public static final Map<String, String> FIELDS = Map.ofEntries(
            Map.entry("p_birth_year", "birth_year"), Map.entry("p_sex", "sex"), Map.entry("p_weight", "weight_kg"),
            Map.entry("p_height", "height_cm"), Map.entry("p_activity", "activity_level"),
            Map.entry("p_days", "days_per_week"), Map.entry("p_minutes", "minutes_per_session"),
            Map.entry("p_preferred_days", "preferred_days"), Map.entry("p_environments", "environments"),
            Map.entry("p_equipment", "equipment"));

    /**
     * Guarda las respuestas del perfil común de forma PARCIAL: solo las columnas presentes en {@code a}; lo demás se
     * conserva (principio de no presión: el usuario completa a su ritmo).
     */
    public void save(UUID userId, Answers a) {
        for (Map.Entry<String, String> e : FIELDS.entrySet()) {
            if (a.has(e.getKey()) || (e.getKey().equals("p_equipment") && a.values().containsKey("p_equipment"))) {
                patchField(userId, e.getValue(), a, e.getKey());
            }
        }
        jdbc.update("UPDATE user_profile SET completed_at = COALESCE(completed_at, now()) WHERE user_id = ?", userId);
    }

    /** Actualiza una sola columna del perfil común a partir de la respuesta {@code questionId}. */
    public void patchField(UUID userId, String column, Answers a, String questionId) {
        if (!FIELDS.containsValue(column)) throw new IllegalArgumentException("Columna no editable: " + column);
        jdbc.update("INSERT INTO user_profile (user_id) VALUES (?) ON CONFLICT (user_id) DO NOTHING", userId);
        jdbc.update(con -> {
            var ps = con.prepareStatement("UPDATE user_profile SET " + column + " = ? WHERE user_id = ?");
            switch (column) {
                case "birth_year", "activity_level", "days_per_week", "minutes_per_session" ->
                    ps.setObject(1, a.integer(questionId));
                case "weight_kg", "height_cm" -> ps.setObject(1, a.number(questionId));
                case "sex" -> ps.setString(1, a.string(questionId));
                case "preferred_days" -> {
                    List<Integer> days = new ArrayList<>();
                    for (String d : a.list(questionId)) {
                        try {
                            days.add(Integer.parseInt(d));
                        } catch (NumberFormatException ignored) {
                            // opción desconocida: se ignora
                        }
                    }
                    ps.setArray(1, JdbcArrays.smallintArray(con, days));
                }
                case "equipment" -> ps.setArray(1, JdbcArrays.textArray(con, a.listWithoutNone(questionId)));
                default -> ps.setArray(1, JdbcArrays.textArray(con, a.list(questionId)));
            }
            ps.setObject(2, userId);
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
        if (!p.equipment().isEmpty()) m.put("p_equipment", p.equipment());
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
