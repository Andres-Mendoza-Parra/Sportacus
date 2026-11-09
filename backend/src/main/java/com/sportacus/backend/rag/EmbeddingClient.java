package com.sportacus.backend.rag;

/** Convierte texto en un vector numérico para buscar por similitud en pgvector. */
public interface EmbeddingClient {

    float[] embed(String text);

    int dimensions();
}
