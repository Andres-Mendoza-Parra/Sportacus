package com.sportacus.backend.rag;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Embeddings 100% locales vía Ollama (sin API externa ni clave): ni las preguntas de los
 * usuarios ni el catálogo salen de esta máquina. Si Ollama no responde, lanza
 * {@link RagUnavailableException} y el llamador decide cómo degradar.
 */
@Service
public class OllamaEmbeddingClient implements EmbeddingClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final String baseUrl;
    private final String model;
    private final int dimensions;

    public OllamaEmbeddingClient(
            @Value("${sportacus.rag.ollama-base-url}") String baseUrl,
            @Value("${sportacus.rag.embedding-model}") String model,
            @Value("${sportacus.rag.embedding-dimensions}") int dimensions) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) {
        try {
            Map<String, Object> body = Map.of("model", model, "prompt", text);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/embeddings"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Ollama (embeddings) respondió " + response.statusCode());
            }
            JsonNode values = json.readTree(response.body()).path("embedding");
            float[] vector = new float[values.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) values.get(i).asDouble();
            }
            return vector;
        } catch (RagUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new RagUnavailableException("No se pudo generar el embedding local", e);
        }
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
