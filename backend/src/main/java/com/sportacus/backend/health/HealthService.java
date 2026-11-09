package com.sportacus.backend.health;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.common.JdbcArrays;
import com.sportacus.backend.common.Json;
import com.sportacus.backend.world.form.FormSpec;

/** Chequeo de salud, condiciones activas y resumen para los mundos. */
@Service
public class HealthService {

    private final JdbcTemplate jdbc;
    private final SensitiveDataCipher cipher;
    private final Json json;

    public HealthService(JdbcTemplate jdbc, SensitiveDataCipher cipher, Json json) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.json = json;
    }

    public FormSpec form(UUID userId) {
        return HealthScreeningForm.form().withAnswers(lastAnswers(userId));
    }

    /** Guarda un chequeo nuevo y reemplaza las condiciones declaradas en él. */
    @Transactional
    public HealthSummary saveScreening(UUID userId, Answers answers) {
        ScreeningRules.Result result = ScreeningRules.evaluate(answers);
        boolean clearanceConfirmed = answers.yes("s_clearance") && !result.redFlags()
                && !result.flags().contains("cardio_sintomas");
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO health_screening (user_id, answers_encrypted, flags, risk_level, requires_clearance,
                                                  clearance_confirmed)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """);
            ps.setObject(1, userId);
            ps.setString(2, cipher.encrypt(json.write(answers.asMap())));
            ps.setArray(3, JdbcArrays.textArray(con, result.flags()));
            ps.setString(4, result.riskLevel());
            ps.setBoolean(5, result.requiresClearance());
            ps.setBoolean(6, clearanceConfirmed);
            return ps;
        });

        jdbc.update("DELETE FROM health_condition WHERE user_id = ?", userId);
        String severity = answers.string("s_pain_severity", "leve");
        for (String zone : answers.listWithoutNone("s_pain_zones")) {
            insertCondition(userId, zone, "lesion", severity);
        }
        for (String d : answers.listWithoutNone("s_diagnosis")) {
            insertCondition(userId, d, "condicion", null);
        }
        if (answers.yes("s_surgery")) {
            insertCondition(userId, "postoperatorio", "cirugia", null);
        }
        if (answers.yes("s_pregnancy")) {
            insertCondition(userId, "embarazo", "embarazo", null);
        }
        if (answers.contains("s_symptoms", "mareos")) {
            insertCondition(userId, "mareos", "condicion", null);
        }
        return summary(userId);
    }

    /** El usuario confirma que un profesional lo autorizó (queda registrado en el último chequeo). */
    public HealthSummary confirmClearance(UUID userId) {
        jdbc.update("""
                UPDATE health_screening SET clearance_confirmed = TRUE
                WHERE id = (SELECT id FROM health_screening WHERE user_id = ? ORDER BY created_at DESC LIMIT 1)
                """, userId);
        return summary(userId);
    }

    public HealthSummary summary(UUID userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT flags, risk_level, requires_clearance, clearance_confirmed, created_at
                FROM health_screening WHERE user_id = ? ORDER BY created_at DESC LIMIT 1
                """, userId);
        List<HealthSummary.ConditionView> conditions = jdbc.query("""
                SELECT c.tag_key, t.label, c.kind, c.severity, t.body_zone
                FROM health_condition c JOIN contraindication_tag t ON t.key = c.tag_key
                WHERE c.user_id = ? AND c.status <> 'resuelta'
                ORDER BY c.created_at
                """, (rs, i) -> new HealthSummary.ConditionView(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5)), userId);
        Set<String> tags = new LinkedHashSet<>();
        conditions.forEach(c -> tags.add(c.tagKey()));
        if (rows.isEmpty()) {
            return new HealthSummary(false, "bajo", List.of(), false, false, false, tags, conditions, null);
        }
        Map<String, Object> r = rows.get(0);
        List<String> flags = new ArrayList<>();
        Object raw = r.get("flags");
        if (raw instanceof java.sql.Array arr) {
            try {
                for (Object o : (Object[]) arr.getArray()) flags.add(String.valueOf(o));
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException(e);
            }
        }
        OffsetDateTime at = r.get("created_at") instanceof Timestamp ts ? ts.toInstant().atOffset(ZoneOffset.UTC) : null;
        return new HealthSummary(true, (String) r.get("risk_level"), flags, (Boolean) r.get("requires_clearance"),
                (Boolean) r.get("clearance_confirmed"), flags.contains("bandera_roja"), tags, conditions, at);
    }

    private Map<String, Object> lastAnswers(UUID userId) {
        List<String> rows = jdbc.queryForList("""
                SELECT answers_encrypted FROM health_screening WHERE user_id = ? ORDER BY created_at DESC LIMIT 1
                """, String.class, userId);
        if (rows.isEmpty()) return Map.of();
        try {
            return json.readMap(cipher.decrypt(rows.get(0)));
        } catch (RuntimeException e) {
            return Map.of(); // clave rotada: el usuario vuelve a contestar
        }
    }

    private void insertCondition(UUID userId, String tag, String kind, String severity) {
        Integer known = jdbc.queryForObject("SELECT count(*) FROM contraindication_tag WHERE key = ?", Integer.class, tag);
        if (known == null || known == 0) return;
        jdbc.update("INSERT INTO health_condition (user_id, tag_key, kind, severity) VALUES (?, ?, ?, ?)",
                userId, tag, kind, severity);
    }
}
