package com.sportacus.backend.rag;

import java.util.List;

/**
 * Convierte texto en vectores para buscar por similitud en pgvector. Los modelos tipo nomic-embed-text distinguen entre
 * el texto que se guarda (documento) y la pregunta con que se busca (consulta): usar el prefijo correcto en cada lado
 * mejora bastante la recuperación.
 */
public interface EmbeddingClient {

    /** Vector de una pregunta del usuario. */
    float[] embedQuery(String text);

    /** Vectores de varios fragmentos de conocimiento, en el mismo orden. */
    List<float[]> embedDocuments(List<String> texts);

    /** Nombre del modelo (se guarda junto al vector para detectar cambios de modelo). */
    String model();

    int dimensions();
}
