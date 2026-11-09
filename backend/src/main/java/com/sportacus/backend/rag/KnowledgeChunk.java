package com.sportacus.backend.rag;

/** Fragmento del catálogo recuperado de {@code knowledge_chunk} por similitud con la pregunta. */
public record KnowledgeChunk(String sourceType, String sourceId, String content, double distance) {
}
