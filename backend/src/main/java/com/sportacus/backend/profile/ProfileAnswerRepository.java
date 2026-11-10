package com.sportacus.backend.profile;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.sportacus.backend.common.Json;
import com.sportacus.backend.health.SensitiveDataCipher;
import com.sportacus.backend.world.metrics.MetricRepository;

/** Tablas profile_answer, profile_question_state y profile_consent (V3). */
@Repository
public class ProfileAnswerRepository {

    private final JdbcTemplate jdbc;
    private final Json json;
    private final SensitiveDataCipher cipher;

    public ProfileAnswerRepository(JdbcTemplate jdbc, Json json, SensitiveDataCipher cipher) {
        this.jdbc = jdbc;
        this.json = json;
        this.cipher = cipher;
    }

    /** Respuesta vigente de una pregunta. */
    public record Latest(Object value, OffsetDateTime answeredAt) {
        public LocalDate day() {
            return answeredAt.atZoneSameInstant(MetricRepository.ZONE).toLocalDate();
        }
    }

    public void insert(UUID userId, String questionKey, String areaKey, Object value, boolean sensitive, String source) {
        String raw = json.write(Map.of("v", value));
        if (sensitive) {
            jdbc.update("""
                    INSERT INTO profile_answer (user_id, question_key, area_key, value_encrypted, source)
                    VALUES (?, ?, ?, ?, ?)
                    """, userId, questionKey, areaKey, cipher.encrypt(raw), source);
        } else {
            jdbc.update("""
                    INSERT INTO profile_answer (user_id, question_key, area_key, value, source)
                    VALUES (?, ?, ?, ?::jsonb, ?)
                    """, userId, questionKey, areaKey, raw, source);
        }
        setState(userId, questionKey, "respondida", null);
    }

    /** Última respuesta de cada pregunta, sin las marcadas como "prefiero no decir". */
    public Map<String, Latest> latest(UUID userId) {
        Map<String, Latest> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON (a.question_key) a.question_key, a.value::text, a.value_encrypted, a.answered_at
                FROM profile_answer a
                LEFT JOIN profile_question_state s ON s.user_id = a.user_id AND s.question_key = a.question_key
                WHERE a.user_id = ? AND (s.status IS NULL OR s.status <> 'no_decir')
                ORDER BY a.question_key, a.answered_at DESC
                """, rs -> {
            String raw = rs.getString(2) != null ? rs.getString(2) : cipher.decrypt(rs.getString(3));
            Object value = json.readMap(raw).get("v");
            Timestamp ts = rs.getTimestamp(4);
            out.put(rs.getString(1), new Latest(value, ts.toInstant().atOffset(ZoneOffset.UTC)));
        }, userId);
        return out;
    }

    public record State(String status, OffsetDateTime until) {
    }

    public Map<String, State> states(UUID userId) {
        Map<String, State> out = new HashMap<>();
        jdbc.query("SELECT question_key, status, until FROM profile_question_state WHERE user_id = ?", rs -> {
            Timestamp until = rs.getTimestamp(3);
            out.put(rs.getString(1), new State(rs.getString(2),
                    until == null ? null : until.toInstant().atOffset(ZoneOffset.UTC)));
        }, userId);
        return out;
    }

    public void setState(UUID userId, String questionKey, String status, OffsetDateTime until) {
        jdbc.update("""
                INSERT INTO profile_question_state (user_id, question_key, status, until) VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id, question_key) DO UPDATE SET status = EXCLUDED.status, until = EXCLUDED.until,
                    updated_at = now()
                """, userId, questionKey, status, until);
    }

    /** Respuestas registradas hoy desde sugerencias (límite diario del motor de preguntas). */
    public int suggestedToday(UUID userId) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM profile_answer WHERE user_id = ? AND source = 'sugerida'
                  AND (answered_at AT TIME ZONE 'America/Bogota')::date = (now() AT TIME ZONE 'America/Bogota')::date
                """, Integer.class, userId);
        return n == null ? 0 : n;
    }

    // ── Consentimiento ────────────────────────────────────────

    /** área → concedido (true/false). Las áreas sin fila no se han decidido. */
    public Map<String, Boolean> consents(UUID userId) {
        Map<String, Boolean> out = new HashMap<>();
        jdbc.query("SELECT area_key, granted FROM profile_consent WHERE user_id = ?",
                rs -> {
                    out.put(rs.getString(1), rs.getBoolean(2));
                }, userId);
        return out;
    }

    public Map<String, OffsetDateTime> consentDates(UUID userId) {
        Map<String, OffsetDateTime> out = new HashMap<>();
        jdbc.query("SELECT area_key, updated_at FROM profile_consent WHERE user_id = ?", rs -> {
            out.put(rs.getString(1), rs.getTimestamp(2).toInstant().atOffset(ZoneOffset.UTC));
        }, userId);
        return out;
    }

    public void setConsent(UUID userId, String areaKey, boolean granted) {
        jdbc.update("""
                INSERT INTO profile_consent (user_id, area_key, granted) VALUES (?, ?, ?)
                ON CONFLICT (user_id, area_key) DO UPDATE SET granted = EXCLUDED.granted, updated_at = now()
                """, userId, areaKey, granted);
    }

    /** Revocar el consentimiento borra todas las respuestas del área (y su estado). */
    public void deleteArea(UUID userId, String areaKey, List<String> questionKeys) {
        jdbc.update("DELETE FROM profile_answer WHERE user_id = ? AND area_key = ?", userId, areaKey);
        for (String q : questionKeys) {
            jdbc.update("DELETE FROM profile_question_state WHERE user_id = ? AND question_key = ?", userId, q);
        }
    }

    /** Historial completo (exportación de datos). */
    public List<Map<String, Object>> export(UUID userId) {
        return jdbc.query("""
                SELECT question_key, area_key, value::text, value_encrypted, source, answered_at
                FROM profile_answer WHERE user_id = ? ORDER BY answered_at
                """, (rs, i) -> {
            String raw = rs.getString(3) != null ? rs.getString(3) : cipher.decrypt(rs.getString(4));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pregunta", rs.getString(1));
            m.put("area", rs.getString(2));
            m.put("valor", json.readMap(raw).get("v"));
            m.put("origen", rs.getString(5));
            m.put("fecha", String.valueOf(rs.getObject(6)));
            return m;
        }, userId);
    }
}
