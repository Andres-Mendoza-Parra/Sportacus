package com.sportacus.backend.rag;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Búsqueda por similitud de coseno en {@code knowledge_chunk} (pgvector). Prioriza los fragmentos
 * del mundo activo y los generales; si pgvector no está instalado devuelve una lista vacía y el
 * asistente responde solo con el contexto del usuario.
 */
@Repository
public class KnowledgeRetrievalService {

    private final JdbcTemplate jdbc;
    private volatile Boolean available;

    public KnowledgeRetrievalService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<KnowledgeChunk> search(float[] queryEmbedding, String worldKey, int topK) {
        if (!isAvailable()) return List.of();
        String vector = toVectorLiteral(queryEmbedding);
        return jdbc.query("""
                SELECT source_type, source_id, content, embedding <=> ?::vector AS distance
                FROM knowledge_chunk
                WHERE ?::text IS NULL OR world_key IS NULL OR world_key = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """,
                (rs, i) -> new KnowledgeChunk(rs.getString(1), rs.getString(2), rs.getString(3), rs.getDouble(4)),
                vector, worldKey, worldKey, vector, topK);
    }

    public boolean isAvailable() {
        if (available == null) available = tableExists(jdbc);
        return available;
    }

    static boolean tableExists(JdbcTemplate jdbc) {
        Boolean exists = jdbc.queryForObject("SELECT to_regclass('knowledge_chunk') IS NOT NULL", Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    /** Formato de texto que pgvector acepta para castear a {@code vector}, ej. "[0.12,-0.4,...]". */
    static String toVectorLiteral(float[] values) {
        StringBuilder sb = new StringBuilder(values.length * 8).append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(values[i]);
        }
        return sb.append(']').toString();
    }
}
