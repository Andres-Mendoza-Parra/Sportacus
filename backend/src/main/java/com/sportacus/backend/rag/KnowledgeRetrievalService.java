package com.sportacus.backend.rag;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Búsqueda semántica en {@code knowledge_chunk} (pgvector, distancia de coseno). Los fragmentos del mundo activo
 * reciben una pequeña ventaja, pero los de otros mundos no se excluyen (alguien en Gimnasio puede preguntar por
 * correr). Se descartan los fragmentos demasiado lejanos para no meter ruido en el prompt. Sin pgvector devuelve
 * una lista vacía y el asistente responde solo con el contexto del usuario.
 */
@Repository
public class KnowledgeRetrievalService {

    private final JdbcTemplate jdbc;
    private final double maxDistance;
    private final double worldBoost;
    private volatile Boolean available;

    public KnowledgeRetrievalService(JdbcTemplate jdbc,
            @Value("${sportacus.rag.max-distance:0.55}") double maxDistance,
            @Value("${sportacus.rag.world-boost:0.05}") double worldBoost) {
        this.jdbc = jdbc;
        this.maxDistance = maxDistance;
        this.worldBoost = worldBoost;
    }

    public List<KnowledgeChunk> search(float[] queryEmbedding, String worldKey, int topK) {
        if (!isAvailable()) return List.of();
        String vector = toVectorLiteral(queryEmbedding);
        // Con unos cientos de fragmentos el recorrido completo tarda milisegundos; la ventaja por mundo impide usar
        // el índice HNSW en el ORDER BY, que queda para cuando el corpus crezca (ver docs/24).
        return jdbc.query("""
                SELECT source_type, source_id, world_key, coalesce(title, source_id), content,
                       metadata->>'reference', distance
                FROM (SELECT *, embedding <=> ?::vector AS distance FROM knowledge_chunk) k
                WHERE distance <= ?
                ORDER BY distance - CASE WHEN ?::text IS NOT NULL AND world_key = ? THEN ? ELSE 0 END
                LIMIT ?
                """,
                (rs, i) -> new KnowledgeChunk(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getDouble(7)),
                vector, maxDistance, worldKey, worldKey, worldBoost, topK);
    }

    public long count() {
        if (!isAvailable()) return 0;
        Long n = jdbc.queryForObject("SELECT count(*) FROM knowledge_chunk", Long.class);
        return n == null ? 0 : n;
    }

    public boolean isAvailable() {
        if (!Boolean.TRUE.equals(available)) available = tableExists(jdbc); // se re-evalúa mientras no exista
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
