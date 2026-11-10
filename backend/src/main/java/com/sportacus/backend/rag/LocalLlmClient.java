package com.sportacus.backend.rag;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Generación de texto 100% local vía Ollama: el chat del asistente ({@code /api/chat}, con roles) y el
 * enriquecimiento de ejercicios ({@code /api/generate} en JSON). Nada de lo que se manda aquí sale de la máquina
 * donde corre el contenedor de Ollama.
 */
@Service
public class LocalLlmClient {

    /** Un turno de la conversación: role = system | user | assistant. */
    public record Message(String role, String content) {
    }

    /** Estado del motor: si responde y si tiene descargados los modelos configurados. */
    public record Status(boolean reachable, boolean chatModelReady, boolean embeddingModelReady, String chatModel,
            String embeddingModel) {

        public boolean ready() {
            return reachable && chatModelReady && embeddingModelReady;
        }
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final String baseUrl;
    private final String model;
    private final String embeddingModel;
    private final int numCtx;
    private final double temperature;
    private final String keepAlive;
    private final Duration timeout;

    public LocalLlmClient(
            @Value("${sportacus.rag.ollama-base-url}") String baseUrl,
            @Value("${sportacus.rag.chat-model}") String model,
            @Value("${sportacus.rag.embedding-model}") String embeddingModel,
            @Value("${sportacus.rag.num-ctx:6144}") int numCtx,
            @Value("${sportacus.rag.temperature:0.3}") double temperature,
            @Value("${sportacus.rag.keep-alive:30m}") String keepAlive,
            @Value("${sportacus.rag.timeout-seconds:120}") int timeoutSeconds) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.embeddingModel = embeddingModel;
        this.numCtx = numCtx;
        this.temperature = temperature;
        this.keepAlive = keepAlive;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    public String model() {
        return model;
    }

    /** Respuesta del asistente a una conversación (mensaje de sistema + historial + pregunta). */
    public String chat(List<Message> messages) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("keep_alive", keepAlive);
        body.put("options", Map.of("temperature", temperature, "num_ctx", numCtx));
        JsonNode reply = post("/api/chat", body).path("message").path("content");
        if (!reply.isString()) throw new RagUnavailableException("El modelo local no devolvió texto", null);
        return reply.asString().trim();
    }

    /** Genera texto libre a partir de un único prompt. */
    public String generate(String prompt) {
        return generate(prompt, false);
    }

    /** @param asJson true pide a Ollama que fuerce una salida JSON válida (para el enriquecimiento estructurado). */
    public String generate(String prompt, boolean asJson) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("stream", false);
        body.put("keep_alive", keepAlive);
        body.put("options", Map.of("temperature", asJson ? 0.4 : 0.5, "num_ctx", numCtx));
        if (asJson) body.put("format", "json");
        JsonNode text = post("/api/generate", body).path("response");
        if (!text.isString()) throw new RagUnavailableException("El modelo local no devolvió texto", null);
        return text.asString().trim();
    }

    /** Carga el modelo de chat en memoria (prompt vacío) para que la primera pregunta no espere la carga. */
    public void warmUp() {
        post("/api/generate", Map.of("model", model, "prompt", "", "keep_alive", keepAlive,
                "options", Map.of("num_ctx", numCtx)));
    }

    public Status status() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return new Status(false, false, false, model, embeddingModel);
            List<String> names = new ArrayList<>();
            for (JsonNode m : json.readTree(response.body()).path("models")) names.add(m.path("name").asString());
            return new Status(true, hasModel(names, model), hasModel(names, embeddingModel), model, embeddingModel);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Status(false, false, false, model, embeddingModel);
        } catch (Exception e) {
            return new Status(false, false, false, model, embeddingModel);
        }
    }

    /** "nomic-embed-text" equivale a "nomic-embed-text:latest". */
    static boolean hasModel(List<String> installed, String wanted) {
        String w = wanted.contains(":") ? wanted : wanted + ":latest";
        return installed.stream().anyMatch(n -> n.equals(wanted) || n.equals(w));
    }

    private JsonNode post(String path, Map<String, Object> body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Ollama (" + path + ") respondió " + response.statusCode() + ": " + response.body());
            }
            return json.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RagUnavailableException("Consulta al modelo local interrumpida", e);
        } catch (Exception e) {
            throw new RagUnavailableException("El modelo local no está disponible", e);
        }
    }
}
