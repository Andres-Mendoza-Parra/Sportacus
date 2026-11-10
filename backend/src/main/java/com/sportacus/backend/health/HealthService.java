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

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.analytics.AnalyticsService;
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
    private final AnalyticsService analytics;

    public HealthService(JdbcTemplate jdbc, SensitiveDataCipher cipher, Json json, AnalyticsService analytics) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.json = json;
        this.analytics = analytics;
    }

    public FormSpec form(UUID userId) {
        return HealthScreeningForm.form().withAnswers(lastAnswers(userId));
    }

    // ── Consentimiento de datos de salud (Ley 1581, art. 6: datos sensibles) ──

    public static final String CONSENT_AREA = "salud";
    public static final String CONSENT_REQUIRED = "Para guardar datos de salud necesitamos tu autorización";

    /** ¿Autorizó expresamente el tratamiento de sus datos de salud? (profile_consent, área "salud"). */
    public boolean hasConsent(UUID userId) {
        List<Boolean> rows = jdbc.queryForList(
                "SELECT granted FROM profile_consent WHERE user_id = ? AND area_key = ?", Boolean.class, userId,
                CONSENT_AREA);
        return !rows.isEmpty() && Boolean.TRUE.equals(rows.get(0));
    }

    /** 403 si no hay autorización. Lo usan el chequeo y el check-in diario. */
    public void requireConsent(UUID userId) {
        if (!hasConsent(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, CONSENT_REQUIRED);
        }
    }

    /**
     * Revocar la autorización borra los datos de salud: chequeos, condiciones, respuestas del check-in diario y las
     * métricas de bienestar y dolor. Lo demás de la cuenta se conserva.
     */
    @Transactional
    public void deleteHealthData(UUID userId, List<String> checkinQuestionIds) {
        jdbc.update("DELETE FROM health_screening WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM health_condition WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM profile_answer WHERE user_id = ? AND area_key = 'checkin'", userId);
        for (String q : checkinQuestionIds) {
            jdbc.update("DELETE FROM profile_question_state WHERE user_id = ? AND question_key = ?", userId, q);
        }
        jdbc.update("DELETE FROM profile_question_state WHERE user_id = ? AND question_key = 'health'", userId);
        jdbc.update("""
                DELETE FROM metric_entry WHERE user_id = ? AND (metric_key LIKE 'bienestar\\_%' OR metric_key = 'dolor_24h')
                """, userId);
    }

    /** Guarda un chequeo nuevo (validado contra el formulario) y reemplaza las condiciones declaradas en él. */
    @Transactional
    public HealthSummary saveScreening(UUID userId, Answers raw) {
        requireConsent(userId);
        Answers answers;
        try {
            answers = new Answers(ScreeningValidator.clean(HealthScreeningForm.form(), raw.asMap()));
        } catch (ScreeningValidator.Invalid e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
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
        if (answers.contains("s_medications", "anticoagulantes")) {
            insertCondition(userId, "anticoagulantes", "condicion", null);
        }
        List<String> mobility = answers.listWithoutNone("s_mobility");
        if (mobility.contains("silla_ruedas") || mobility.contains("ayuda_marcha") || mobility.contains("amputacion")) {
            insertCondition(userId, "movilidad_reducida", "condicion", null);
        }
        if (answers.contains("s_symptoms", "mareos")) {
            insertCondition(userId, "mareos", "condicion", null);
        }
        analytics.track(userId, "health_screening_saved", Map.of("risk", result.riskLevel()));
        return summary(userId);
    }

    /**
     * La persona confirma que un profesional la autorizó (queda en el último chequeo). Regla única (auditoría A4): se
     * permite con síntomas cardiovasculares —es justo lo que pide el aviso—, pero no con banderas rojas: esas
     * señales se resuelven con un chequeo nuevo cuando ya hayan sido revisadas.
     */
    public HealthSummary confirmClearance(UUID userId) {
        HealthSummary current = summary(userId);
        if (!current.screened()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Primero haz tu chequeo de salud");
        }
        if (current.redFlags()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vuelve a hacer el chequeo cuando las señales hayan sido revisadas por un profesional");
        }
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

    /** Respuestas del último chequeo (descifradas). Las usa el Perfil 360 para condiciones de visibilidad. */
    public Map<String, Object> lastAnswers(UUID userId) {
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
