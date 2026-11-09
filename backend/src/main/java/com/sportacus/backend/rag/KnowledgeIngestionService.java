package com.sportacus.backend.rag;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.sportacus.backend.common.Json;
import com.sportacus.backend.world.catalog.CatalogModels.AssessmentInfo;
import com.sportacus.backend.world.catalog.CatalogModels.ProgressionInfo;
import com.sportacus.backend.world.catalog.CatalogModels.TrackInfo;
import com.sportacus.backend.world.catalog.CatalogModels.WorldInfo;
import com.sportacus.backend.world.catalog.CatalogModels.WorldMovement;
import com.sportacus.backend.world.catalog.CatalogRepository;

/**
 * Convierte el catálogo por mundos (mundos, rutas, movimientos, progresiones y tests) en fragmentos
 * vectorizados de {@code knowledge_chunk}, cada uno etiquetado con su {@code world_key} para que el
 * asistente recupere primero el conocimiento del mundo activo. Se dispara a mano
 * (POST /api/admin/rag/reindex) para no bloquear el arranque si Ollama no tiene los modelos.
 */
@Service
public class KnowledgeIngestionService {

    private final JdbcTemplate jdbc;
    private final EmbeddingClient embeddingClient;
    private final CatalogRepository catalog;
    private final Json json;

    public KnowledgeIngestionService(JdbcTemplate jdbc, EmbeddingClient embeddingClient, CatalogRepository catalog,
            Json json) {
        this.jdbc = jdbc;
        this.embeddingClient = embeddingClient;
        this.catalog = catalog;
        this.json = json;
    }

    /** @return cuántos fragmentos se (re)generaron. */
    public int reindexAll() {
        if (!KnowledgeRetrievalService.tableExists(jdbc)) {
            throw new IllegalStateException("pgvector no está instalado: no existe knowledge_chunk (ver V2__rag_opcional.sql)");
        }
        int count = 0;
        for (WorldInfo w : catalog.worlds()) {
            upsert("world", w.key(), w.key(), "Mundo " + w.name() + ". " + w.tagline() + ". " + w.description()
                    + ". Destacados: " + String.join("; ", w.highlights()));
            count++;
            for (TrackInfo t : w.tracks()) {
                upsert("track", w.key() + ":" + t.key(), w.key(),
                        "Ruta " + t.name() + " del mundo " + w.name() + ". " + t.description());
                count++;
            }
            for (WorldMovement m : catalog.movements(w.key())) {
                upsert("movement", w.key() + ":" + m.key(), w.key(), movementText(w, m));
                count++;
            }
            for (ProgressionInfo p : catalog.progressions(w.key())) {
                StringBuilder sb = new StringBuilder("Progresión " + p.name() + " (" + w.name() + ")");
                if (p.description() != null) sb.append(". ").append(p.description());
                sb.append(". Escalones en orden: ");
                p.steps().forEach(s -> sb.append(s.index() + 1).append(") ").append(s.name()).append("; "));
                upsert("progression", p.key(), w.key(), sb.toString());
                count++;
            }
            for (AssessmentInfo a : catalog.assessments(w.key())) {
                upsert("assessment", a.key(), w.key(), "Test " + a.name() + " (" + w.name() + "). " + a.description()
                        + ". Instrucciones: " + String.join(" ", a.instructions())
                        + (a.safetyNote() == null ? "" : ". Seguridad: " + a.safetyNote()));
                count++;
            }
        }
        return count;
    }

    private String movementText(WorldInfo w, WorldMovement wm) {
        var m = wm.movement();
        StringBuilder sb = new StringBuilder("Movimiento: ").append(m.name()).append(" (mundo ").append(w.name()).append(")");
        append(sb, "Descripción", m.description());
        append(sb, "Músculos", String.join(", ", m.muscles()));
        append(sb, "Equipo", m.equipment().isEmpty() ? "sin equipo" : String.join(", ", m.equipment()));
        append(sb, "Nivel (1-5)", String.valueOf(wm.level()));
        append(sb, "Patrón", wm.pattern());
        append(sb, "Pasos", String.join(" ", m.steps()));
        append(sb, "Claves técnicas", String.join(" ", m.cues()));
        append(sb, "Errores comunes", String.join("; ", m.commonMistakes()));
        if (!m.contraindications().isEmpty()) {
            append(sb, "Contraindicaciones", String.join(", ", m.contraindications().stream()
                    .map(c -> catalog.tagLabels().getOrDefault(c.tag(), c.tag()) + " (" + c.level() + ")").toList()));
        }
        return sb.toString();
    }

    private void upsert(String sourceType, String sourceId, String worldKey, String content) {
        String vector = KnowledgeRetrievalService.toVectorLiteral(embeddingClient.embed(content));
        jdbc.update("""
                INSERT INTO knowledge_chunk (source_type, source_id, world_key, content, embedding, metadata, updated_at)
                VALUES (?, ?, ?, ?, ?::vector, ?::jsonb, now())
                ON CONFLICT (source_type, source_id) DO UPDATE
                    SET world_key = EXCLUDED.world_key, content = EXCLUDED.content, embedding = EXCLUDED.embedding,
                        metadata = EXCLUDED.metadata, updated_at = now()
                """, sourceType, sourceId, worldKey, content, vector, json.write(Map.of("world", worldKey)));
    }

    private static void append(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) return;
        sb.append(". ").append(label).append(": ").append(value.trim());
    }
}
