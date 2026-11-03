package com.sportacus.backend.media;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Complementa la descripción, los pasos y el equipo de un ejercicio con Gemini. El resultado se guarda
 * en {@code exercise_enrichment} (una fila por ejercicio, común a todos los usuarios): solo la primera
 * visita de un ejercicio llama a la IA; las demás leen la base de datos. Tolerante a fallos: sin clave,
 * sin tabla, sin cuota o ante cualquier error devuelve vacío y la app sigue con el texto de wger.
 * La clave viaja solo por la variable de entorno {@code GEMINI_API_KEY} y en un header, nunca en la URL.
 */
@Service
public class ExerciseEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseEnrichmentService.class);

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(20);
    private static final long RETRY_AFTER_FAILURE_MS = 60_000;
    /** Tras un 429/503 de Gemini no se vuelve a llamar a nadie durante este tiempo (protege la cuota gratuita). */
    private static final long COOLDOWN_AFTER_LIMIT_MS = 10 * 60_000;

    /** Texto complementario ya listo para mostrar. Cualquier campo puede venir vacío. */
    public record Enrichment(String description, List<String> steps, List<String> commonMistakes, String equipment) {
        public static Enrichment empty() {
            return new Enrichment(null, List.of(), List.of(), null);
        }

        public boolean isEmpty() {
            return (description == null || description.isBlank()) && steps.isEmpty();
        }
    }

    private final JdbcTemplate jdbc;
    private final String apiKey;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> failedAt = new ConcurrentHashMap<>();
    /** Máximo 2 llamadas simultáneas: una pantalla que abre varios ejercicios no debe disparar una ráfaga. */
    private final Semaphore inFlight = new Semaphore(2);
    private volatile long limitedUntil;

    public ExerciseEnrichmentService(JdbcTemplate jdbc,
            @Value("${sportacus.gemini.api-key:}") String apiKey,
            @Value("${sportacus.gemini.model:gemini-3.1-flash-lite}") String model) {
        this.jdbc = jdbc;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
    }

    /**
     * @param exerciseId ejercicio del catálogo
     * @param baseText   lo que ya sabemos (descripción, pasos de wger, etc.) para que la IA complemente y no invente
     */
    public Enrichment lookup(UUID exerciseId, String name, String muscleGroup, String equipment, String baseText) {
        try {
            Enrichment stored = load(exerciseId);
            if (stored != null) {
                return stored;
            }
            if (apiKey.isEmpty() || System.currentTimeMillis() < limitedUntil) {
                return Enrichment.empty();
            }
            Long failed = failedAt.get(exerciseId);
            if (failed != null && System.currentTimeMillis() - failed < RETRY_AFTER_FAILURE_MS) {
                return Enrichment.empty();
            }
            // Un solo hilo por ejercicio: si otro usuario lo abre a la vez, espera y lee lo que se guardó.
            synchronized (locks.computeIfAbsent(exerciseId, k -> new Object())) {
                stored = load(exerciseId);
                if (stored != null) {
                    return stored;
                }
                Enrichment generated;
                if (!inFlight.tryAcquire(CALL_TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS)) {
                    return Enrichment.empty();
                }
                try {
                    generated = generate(name, muscleGroup, equipment, baseText);
                } finally {
                    inFlight.release();
                }
                if (generated.isEmpty()) {
                    failedAt.put(exerciseId, System.currentTimeMillis());
                    return Enrichment.empty();
                }
                save(exerciseId, generated);
                return generated;
            }
        } catch (Exception e) {
            failedAt.put(exerciseId, System.currentTimeMillis());
            log.warn("Sin enriquecimiento IA para '{}': {}", name, e.toString());
            return Enrichment.empty();
        }
    }

    private Enrichment load(UUID exerciseId) throws Exception {
        List<String[]> rows = jdbc.query("""
                SELECT description, steps::text, common_mistakes::text, equipment
                FROM sportacus.exercise_enrichment WHERE exercise_id = ?
                """, (rs, i) -> new String[] { rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4) },
                exerciseId);
        if (rows.isEmpty()) {
            return null;
        }
        String[] r = rows.get(0);
        return new Enrichment(r[0], strings(json.readTree(r[1])), strings(json.readTree(r[2])), r[3]);
    }

    private void save(UUID exerciseId, Enrichment e) throws Exception {
        jdbc.update("""
                INSERT INTO sportacus.exercise_enrichment
                    (exercise_id, description, steps, common_mistakes, equipment, model)
                VALUES (?, ?, ?::jsonb, ?::jsonb, ?, ?)
                ON CONFLICT (exercise_id) DO NOTHING
                """, exerciseId, e.description(), json.writeValueAsString(e.steps()),
                json.writeValueAsString(e.commonMistakes()), e.equipment(), model);
    }

    // ───────────────────────── Gemini ─────────────────────────

    private Enrichment generate(String name, String muscleGroup, String equipment, String baseText) throws Exception {
        String prompt = """
                Eres un entrenador personal certificado. Redacta en español neutro el contenido de la ficha del \
                ejercicio "%s" (grupo muscular: %s; equipo conocido: %s).
                Información de partida (puede estar incompleta, desordenada o repetida; úsala como base y no la contradigas):
                %s

                Reglas:
                - description: 1 o 2 frases que expliquen qué trabaja y para qué sirve. No repitas los pasos.
                - steps: de 4 a 6 pasos de ejecución, cortos, en imperativo, sin numerar y distintos entre sí.
                - commonMistakes: de 2 a 3 errores frecuentes de técnica.
                - equipment: el equipo necesario en español, o "Sin equipo" si no necesita ninguno.
                - No des consejos médicos, cargas concretas, diagnósticos ni promesas de resultados.
                """.formatted(name, nullToDash(muscleGroup), nullToDash(equipment), nullToDash(baseText));

        Map<String, Object> schema = Map.of("type", "OBJECT",
                "properties", Map.of(
                        "description", Map.of("type", "STRING"),
                        "steps", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
                        "commonMistakes", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
                        "equipment", Map.of("type", "STRING")),
                "required", List.of("description", "steps", "commonMistakes", "equipment"));
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json", "responseSchema", schema,
                        "temperature", 0.4));

        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT.formatted(model)))
                .timeout(CALL_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            // No se registra el cuerpo completo: no hay datos sensibles, pero tampoco hace falta ruido.
            if (response.statusCode() == 429 || response.statusCode() == 503) {
                limitedUntil = System.currentTimeMillis() + COOLDOWN_AFTER_LIMIT_MS;
            }
            throw new IllegalStateException("Gemini respondió " + response.statusCode());
        }
        JsonNode text = json.readTree(response.body()).path("candidates").path(0).path("content").path("parts")
                .path(0).path("text");
        if (!text.isString()) {
            return Enrichment.empty();
        }
        JsonNode out = json.readTree(text.asString());
        return new Enrichment(clean(out.path("description").asString("")), strings(out.path("steps")),
                strings(out.path("commonMistakes")), clean(out.path("equipment").asString("")));
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode n : array) {
            String v = clean(n.asString(""));
            if (v != null) values.add(v);
        }
        return values;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.replaceAll("\\s+", " ").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
