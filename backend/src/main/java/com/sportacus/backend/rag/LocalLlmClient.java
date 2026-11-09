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
 * Generación de texto 100% local vía Ollama: sustituye tanto al agente de n8n (chat) como a la
 * llamada a Gemini (enriquecimiento de ejercicios). Nada de lo que se manda aquí sale de la
 * máquina donde corre el contenedor de Ollama.
 */
@Service
public class LocalLlmClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final String baseUrl;
    private final String model;

    public LocalLlmClient(
            @Value("${sportacus.rag.ollama-base-url}") String baseUrl,
            @Value("${sportacus.rag.chat-model}") String model) {
        this.baseUrl = baseUrl;
        this.model = model;
    }

    /** Genera texto libre (respuesta del chat). */
    public String generate(String prompt) {
        return generate(prompt, false);
    }

    /** @param json true pide a Ollama que fuerce una salida JSON válida (para el enriquecimiento estructurado). */
    public String generate(String prompt, boolean json) {
        try {
            Map<String, Object> body = json
                    ? Map.of("model", model, "prompt", prompt, "stream", false, "format", "json",
                            "options", Map.of("temperature", 0.4))
                    : Map.of("model", model, "prompt", prompt, "stream", false,
                            "options", Map.of("temperature", 0.5));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/generate"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(this.json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Ollama (generate) respondió " + response.statusCode());
            }
            JsonNode text = this.json.readTree(response.body()).path("response");
            if (!text.isString()) {
                throw new IllegalStateException("Ollama no devolvió texto");
            }
            return text.asString().trim();
        } catch (RagUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new RagUnavailableException("El modelo local no está disponible", e);
        }
    }
}
