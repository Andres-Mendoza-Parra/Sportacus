package com.sportacus.backend.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.sportacus.backend.common.Json;

/**
 * Mantiene {@code knowledge_chunk} al día con {@link KnowledgeCorpus}, de forma incremental: solo genera vectores
 * para los fragmentos nuevos o cuyo texto cambió (huella SHA-256), borra los que ya no existen y rehace todo si cambia
 * el modelo de embeddings. Se ejecuta al arrancar ({@link RagStartupIndexer}) y a pedido de un administrador.
 */
@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);
    private static final int BATCH = 16;

    private final JdbcTemplate jdbc;
    private final EmbeddingClient embeddings;
    private final KnowledgeCorpus corpus;
    private final KnowledgeRetrievalService retrieval;
    private final Json json;
    private final ReentrantLock running = new ReentrantLock();

    public KnowledgeIngestionService(JdbcTemplate jdbc, EmbeddingClient embeddings, KnowledgeCorpus corpus,
            KnowledgeRetrievalService retrieval, Json json) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
        this.corpus = corpus;
        this.retrieval = retrieval;
        this.json = json;
    }

    /** Resultado de una indexación: cuántos fragmentos hay, cuántos se vectorizaron, cuántos seguían iguales y cuántos se borraron. */
    public record IndexReport(int total, int embedded, int unchanged, int removed, long millis) {
    }

    /** @param force true vuelve a generar todos los vectores aunque el texto no haya cambiado. */
    public IndexReport reindex(boolean force) {
        if (!retrieval.isAvailable()) {
            throw new IllegalStateException("pgvector no está instalado: no existe knowledge_chunk (ver docs/24)");
        }
        if (!running.tryLock()) {
            throw new IllegalStateException("Ya hay una indexación en curso");
        }
        try {
            long start = System.currentTimeMillis();
            List<KnowledgeDoc> docs = corpus.build();
            Map<String, String> existing = new HashMap<>();
            jdbc.query("SELECT source_type || '|' || source_id, coalesce(content_hash, '') || '|' || coalesce(embedding_model, '') FROM knowledge_chunk",
                    rs -> {
                        existing.put(rs.getString(1), rs.getString(2));
                    });
            String model = embeddings.model();
            List<KnowledgeDoc> pending = new ArrayList<>();
            Set<String> keep = new HashSet<>();
            for (KnowledgeDoc d : docs) {
                String key = d.sourceType() + "|" + d.sourceId();
                if (!keep.add(key)) continue; // claves repetidas en el catálogo: se queda la primera
                if (force || !(d.hash() + "|" + model).equals(existing.get(key))) pending.add(d);
            }
            for (int i = 0; i < pending.size(); i += BATCH) {
                List<KnowledgeDoc> batch = pending.subList(i, Math.min(i + BATCH, pending.size()));
                List<float[]> vectors = embeddings.embedDocuments(batch.stream().map(KnowledgeDoc::embeddingText).toList());
                for (int j = 0; j < batch.size(); j++) upsert(batch.get(j), vectors.get(j), model);
            }
            int removed = 0;
            for (String key : existing.keySet()) {
                if (keep.contains(key)) continue;
                String[] parts = key.split("\\|", 2);
                removed += jdbc.update("DELETE FROM knowledge_chunk WHERE source_type = ? AND source_id = ?", parts[0], parts[1]);
            }
            IndexReport report = new IndexReport(keep.size(), pending.size(), keep.size() - pending.size(), removed,
                    System.currentTimeMillis() - start);
            log.info("Memoria del asistente: {} fragmentos ({} vectorizados, {} sin cambios, {} borrados) en {} ms",
                    report.total(), report.embedded(), report.unchanged(), report.removed(), report.millis());
            return report;
        } finally {
            running.unlock();
        }
    }

    public boolean isRunning() {
        return running.isLocked();
    }

    private void upsert(KnowledgeDoc d, float[] vector, String model) {
        Map<String, Object> metadata = new HashMap<>();
        if (d.worldKey() != null) metadata.put("world", d.worldKey());
        if (d.reference() != null) metadata.put("reference", d.reference());
        jdbc.update("""
                INSERT INTO knowledge_chunk (source_type, source_id, world_key, title, content, embedding, metadata,
                                             content_hash, embedding_model, updated_at)
                VALUES (?, ?, ?, ?, ?, ?::vector, ?::jsonb, ?, ?, now())
                ON CONFLICT (source_type, source_id) DO UPDATE
                    SET world_key = EXCLUDED.world_key, title = EXCLUDED.title, content = EXCLUDED.content,
                        embedding = EXCLUDED.embedding, metadata = EXCLUDED.metadata,
                        content_hash = EXCLUDED.content_hash, embedding_model = EXCLUDED.embedding_model, updated_at = now()
                """, d.sourceType(), d.sourceId(), d.worldKey(), truncate(d.title(), 160), d.content(),
                KnowledgeRetrievalService.toVectorLiteral(vector), json.write(metadata), d.hash(), model);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
