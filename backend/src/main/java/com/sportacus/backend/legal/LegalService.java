package com.sportacus.backend.legal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.analytics.AnalyticsService;
import com.sportacus.backend.auth.AgePolicy;
import com.sportacus.backend.common.Json;

/**
 * Política de tratamiento de datos y términos (Ley 1581 de 2012, Decreto 1377 de 2013). Los textos viven en
 * {@code content/legal/*.json}; cambiar un texto exige subir su {@code version}, y la app pide aceptarlo de nuevo a
 * quien tenga una versión anterior.
 */
@Service
public class LegalService {

    public static final List<String> DOCUMENTS = List.of(ConsentLogRepository.POLICY, ConsentLogRepository.TERMS);
    public static final String HEALTH_AREA = "salud";
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    public record Section(String title, String body) {
    }

    public record LegalDocument(String key, String version, String title, String updatedAt, String summary,
            List<Section> sections) {
    }

    /** Estado legal de la cuenta (va en AccountView.legal). Las versiones aceptadas salen como null si no hay. */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public record LegalStatus(String policyVersion, String acceptedPolicyVersion, String termsVersion,
            String acceptedTermsVersion, boolean healthDataConsent, boolean minor, boolean upToDate) {
    }

    private final Map<String, LegalDocument> documents;
    private final ConsentLogRepository consentLog;
    private final JdbcTemplate jdbc;
    private final AnalyticsService analytics;

    public LegalService(Json json, ConsentLogRepository consentLog, JdbcTemplate jdbc, AnalyticsService analytics) {
        this.consentLog = consentLog;
        this.jdbc = jdbc;
        this.analytics = analytics;
        Map<String, LegalDocument> docs = new LinkedHashMap<>();
        for (String key : DOCUMENTS) {
            LegalDocument d = load(json, key);
            if (!key.equals(d.key()) || d.version() == null || d.sections() == null || d.sections().isEmpty()) {
                throw new IllegalStateException("Documento legal inválido: content/legal/" + key + ".json");
            }
            docs.put(key, d);
        }
        this.documents = Map.copyOf(docs);
    }

    private static LegalDocument load(Json json, String key) {
        try (InputStream in = new ClassPathResource("content/legal/" + key + ".json").getInputStream()) {
            return json.read(new String(in.readAllBytes(), StandardCharsets.UTF_8), LegalDocument.class);
        } catch (IOException e) {
            throw new IllegalStateException("No se encontró content/legal/" + key + ".json", e);
        }
    }

    public List<LegalDocument> documents() {
        List<LegalDocument> out = new ArrayList<>();
        DOCUMENTS.forEach(k -> out.add(documents.get(k)));
        return out;
    }

    public Map<String, String> currentVersions() {
        Map<String, String> out = new LinkedHashMap<>();
        DOCUMENTS.forEach(k -> out.put(k, documents.get(k).version()));
        return out;
    }

    public String currentVersion(String key) {
        return documents.get(key).version();
    }

    /** Aceptación explícita desde la app (re-aceptación tras un cambio de versión, o aceptación tardía). */
    public LegalStatus accept(UUID userId, String document, String version) {
        LegalVersions.acceptanceProblem(document, version, currentVersions()).ifPresent(msg -> {
            throw new ResponseStatusException(
                    documents.containsKey(document) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST, msg);
        });
        consentLog.record(userId, document, version.trim(), true, "reaceptacion");
        analytics.track(userId, "policy_accepted", Map.of("document", document, "version", version.trim()));
        return status(userId);
    }

    /** Lo que se acepta al crear la cuenta (misma transacción que el alta). */
    public void recordRegistration(UUID userId, boolean minorWithGuardian, boolean healthData) {
        consentLog.record(userId, ConsentLogRepository.POLICY, currentVersion(ConsentLogRepository.POLICY), true, "registro");
        consentLog.record(userId, ConsentLogRepository.TERMS, currentVersion(ConsentLogRepository.TERMS), true, "registro");
        if (minorWithGuardian) {
            consentLog.record(userId, ConsentLogRepository.GUARDIAN, currentVersion(ConsentLogRepository.TERMS), true,
                    "registro");
        }
        if (healthData) {
            consentLog.record(userId, ConsentLogRepository.HEALTH_DATA, currentVersion(ConsentLogRepository.POLICY),
                    true, "registro");
            jdbc.update("""
                    INSERT INTO profile_consent (user_id, area_key, granted) VALUES (?, ?, TRUE)
                    ON CONFLICT (user_id, area_key) DO UPDATE SET granted = TRUE, updated_at = now()
                    """, userId, HEALTH_AREA);
        }
    }

    public LegalStatus status(UUID userId) {
        Map<String, String> accepted = consentLog.acceptedVersions(userId);
        String policy = currentVersion(ConsentLogRepository.POLICY);
        String terms = currentVersion(ConsentLogRepository.TERMS);
        String acceptedPolicy = accepted.get(ConsentLogRepository.POLICY);
        String acceptedTerms = accepted.get(ConsentLogRepository.TERMS);
        List<Boolean> health = jdbc.queryForList(
                "SELECT granted FROM profile_consent WHERE user_id = ? AND area_key = ?", Boolean.class, userId,
                HEALTH_AREA);
        List<Integer> birth = jdbc.queryForList("SELECT birth_year FROM user_profile WHERE user_id = ?", Integer.class,
                userId);
        Integer birthYear = birth.isEmpty() ? null : birth.get(0);
        boolean minor = AgePolicy.isMinor(birthYear, LocalDate.now(ZONE).getYear());
        return new LegalStatus(policy, acceptedPolicy, terms, acceptedTerms,
                !health.isEmpty() && Boolean.TRUE.equals(health.get(0)), minor,
                LegalVersions.upToDate(policy, acceptedPolicy) && LegalVersions.upToDate(terms, acceptedTerms));
    }
}
