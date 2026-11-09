package com.sportacus.backend.media;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.sportacus.backend.rag.LocalLlmClient;
import com.sportacus.backend.rag.RagUnavailableException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Complementa la descripción, los pasos y el equipo de un ejercicio con el modelo local servido
 * por Ollama (antes llamaba a Gemini: ningún dato del catálogo sale ya de esta máquina). El
 * resultado se guarda en {@code movement_enrichment} (una fila por movimiento, común a todos los
 * usuarios): solo la primera visita de un ejercicio llama al modelo; las demás leen la base de
 * datos. Tolerante a fallos: si Ollama no está disponible, devuelve vacío y la app sigue con el
 * texto de wger.
 */
@Service
public class ExerciseEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseEnrichmentService.class);

    private static final long RETRY_AFTER_FAILURE_MS = 60_000;

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
    private final LocalLlmClient llmClient;
    private final String model;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final Map<String, Long> failedAt = new ConcurrentHashMap<>();
    /** Máximo 2 llamadas simultáneas: una pantalla que abre varios ejercicios no debe disparar una ráfaga. */
    private final Semaphore inFlight = new Semaphore(2);

    public ExerciseEnrichmentService(JdbcTemplate jdbc, LocalLlmClient llmClient,
            @Value("${sportacus.rag.chat-model}") String model) {
        this.jdbc = jdbc;
        this.llmClient = llmClient;
        this.model = model;
    }

    /**
     * @param exerciseId ejercicio del catálogo
     * @param baseText   lo que ya sabemos (descripción, pasos de wger, etc.) para que la IA complemente y no invente
     */
    public Enrichment lookup(String exerciseId, String name, String muscleGroup, String equipment, String baseText) {
        try {
            Enrichment stored = load(exerciseId);
            if (stored != null) {
                return stored;
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
                if (!inFlight.tryAcquire(20, java.util.concurrent.TimeUnit.SECONDS)) {
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

    private Enrichment load(String exerciseId) throws Exception {
        List<String[]> rows = jdbc.query("""
                SELECT description, steps::text, common_mistakes::text, NULL
                FROM movement_enrichment WHERE movement_key = ?
                """, (rs, i) -> new String[] { rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4) },
                exerciseId);
        if (rows.isEmpty()) {
            return null;
        }
        String[] r = rows.get(0);
        return new Enrichment(r[0], strings(json.readTree(r[1])), strings(json.readTree(r[2])), r[3]);
    }

    private void save(String exerciseId, Enrichment e) throws Exception {
        jdbc.update("""
                INSERT INTO movement_enrichment
                    (movement_key, description, steps, common_mistakes, model)
                VALUES (?, ?, ?::jsonb, ?::jsonb, ?)
                ON CONFLICT (movement_key) DO NOTHING
                """, exerciseId, e.description(), json.writeValueAsString(e.steps()),
                json.writeValueAsString(e.commonMistakes()), model);
    }

    // ───────────────────────── Ollama (local) ─────────────────────────

    private Enrichment generate(String name, String muscleGroup, String equipment, String baseText) {
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
                - Responde SOLO con un JSON con las claves exactas: description (string), steps (array de strings), \
                commonMistakes (array de strings), equipment (string). Sin texto adicional antes o después.
                """.formatted(name, nullToDash(muscleGroup), nullToDash(equipment), nullToDash(baseText));

        try {
            String response = llmClient.generate(prompt, true);
            JsonNode out = json.readTree(response);
            return new Enrichment(clean(out.path("description").asString("")), strings(out.path("steps")),
                    strings(out.path("commonMistakes")), clean(out.path("equipment").asString("")));
        } catch (RagUnavailableException e) {
            return Enrichment.empty();
        } catch (Exception e) {
            log.warn("Respuesta del modelo local no interpretable para '{}': {}", name, e.toString());
            return Enrichment.empty();
        }
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
