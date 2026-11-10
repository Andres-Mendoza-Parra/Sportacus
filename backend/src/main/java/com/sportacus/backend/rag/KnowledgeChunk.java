package com.sportacus.backend.rag;

/**
 * Fragmento recuperado de {@code knowledge_chunk} por similitud con la pregunta.
 *
 * @param distance distancia de coseno (0 = idéntico, 2 = opuesto); menor es más relevante
 */
public record KnowledgeChunk(String sourceType, String sourceId, String worldKey, String title, String content,
        String reference, double distance) {
}
