package com.sportacus.backend.rag;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Embeddings 100% locales vía Ollama ({@code POST /api/embed}, por lotes): ni las preguntas de los usuarios ni el
 * catálogo salen de esta máquina. Si Ollama no responde, lanza {@link RagUnavailableException} y el llamador decide
 * cómo degradar.
 */
@Service
public class OllamaEmbeddingClient implements EmbeddingClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final String baseUrl;
    private final String model;
    private final int dimensions;
    private final String queryPrefix;
    private final String documentPrefix;
    private final String keepAlive;

    public OllamaEmbeddingClient(
            @Value("${sportacus.rag.ollama-base-url}") String baseUrl,
            @Value("${sportacus.rag.embedding-model}") String model,
            @Value("${sportacus.rag.embedding-dimensions}") int dimensions,
            @Value("${sportacus.rag.query-prefix:search_query: }") String queryPrefix,
            @Value("${sportacus.rag.document-prefix:search_document: }") String documentPrefix,
            @Value("${sportacus.rag.keep-alive:30m}") String keepAlive) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.dimensions = dimensions;
        this.queryPrefix = queryPrefix;
        this.documentPrefix = documentPrefix;
        this.keepAlive = keepAlive;
    }

    @Override
    public float[] embedQuery(String text) {
        return embed(List.of(queryPrefix + text)).get(0);
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        return embed(texts.stream().map(t -> documentPrefix + t).toList());
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    private List<float[]> embed(List<String> inputs) {
        if (inputs.isEmpty()) return List.of();
        try {
            Map<String, Object> body = Map.of("model", model, "input", inputs, "truncate", true, "keep_alive", keepAlive);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/embed"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Ollama (embed) respondió " + response.statusCode() + ": " + response.body());
            }
            JsonNode all = json.readTree(response.body()).path("embeddings");
            if (all.size() != inputs.size()) {
                throw new IllegalStateException("Ollama devolvió " + all.size() + " vectores para " + inputs.size() + " textos");
            }
            List<float[]> vectors = new ArrayList<>(all.size());
            for (JsonNode values : all) {
                if (values.size() != dimensions) {
                    throw new IllegalStateException("El modelo " + model + " devuelve " + values.size()
                            + " dimensiones y la tabla espera " + dimensions);
                }
                float[] vector = new float[values.size()];
                for (int i = 0; i < vector.length; i++) vector[i] = (float) values.get(i).asDouble();
                vectors.add(vector);
            }
            return vectors;
        } catch (RagUnavailableException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RagUnavailableException("Generación de embeddings interrumpida", e);
        } catch (Exception e) {
            throw new RagUnavailableException("No se pudo generar el embedding local", e);
        }
    }
}
