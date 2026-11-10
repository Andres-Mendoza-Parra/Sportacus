package com.sportacus.backend.profile;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.health.HealthService;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.core.ProfileFacts;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.metrics.MetricRepository;

/**
 * Reúne lo que se sabe del usuario en un solo mapa (id de pregunta → valor): respuestas del Perfil 360, perfil común
 * (manda en los p_*), país/ciudad de la cuenta y respuestas del último chequeo de salud (s_*).
 */
@Service
public class ProfileFactsService {

    private final ProfileAnswerRepository answers;
    private final ProfileRepository profiles;
    private final HealthService health;
    private final JdbcTemplate jdbc;

    public ProfileFactsService(ProfileAnswerRepository answers, ProfileRepository profiles, HealthService health,
            JdbcTemplate jdbc) {
        this.answers = answers;
        this.profiles = profiles;
        this.health = health;
        this.jdbc = jdbc;
    }

    public ProfileFacts facts(UUID userId) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, LocalDate> dates = new LinkedHashMap<>();
        health.lastAnswers(userId).forEach(values::put);
        answers.latest(userId).forEach((k, v) -> {
            values.put(k, v.value());
            dates.put(k, v.day());
        });
        values.putAll(profiles.asAnswers(userId)); // el perfil común es la fuente de verdad de los p_*
        // "Prefiero no decir" manda: el dato no se usa en el Perfil 360 aunque la columna exista (el año de nacimiento
        // se conserva en el perfil común solo por la política de edad y los topes de seguridad).
        answers.states(userId).forEach((k, s) -> {
            if ("no_decir".equals(s.status())) {
                values.remove(k);
                dates.remove(k);
            }
        });
        List<Map<String, Object>> account = jdbc.queryForList("SELECT country, city FROM app_user WHERE id = ?", userId);
        if (!account.isEmpty()) {
            if (account.get(0).get("country") != null) values.put("ub_pais", account.get(0).get("country"));
            if (account.get(0).get("city") != null) values.put("ub_ciudad", account.get(0).get("city"));
        }
        return new ProfileFacts(new Answers(values), dates);
    }

    public ProfileInsights insights(UUID userId, HealthSummary h) {
        return InsightRules.derive(facts(userId), h, LocalDate.now(MetricRepository.ZONE));
    }
}
