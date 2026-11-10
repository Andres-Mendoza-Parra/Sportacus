package com.sportacus.backend.rag;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Al arrancar (después de que {@code ContentLoader} cargó el catálogo) pone al día la memoria del asistente en segundo
 * plano: espera a que Ollama responda y tenga los modelos (la primera vez se están descargando), reindexa solo lo que
 * cambió y precarga el modelo de chat. Nunca bloquea ni tumba el arranque: si Ollama no aparece, lo registra y el
 * chat responde 503 hasta que esté disponible.
 */
@Component
public class RagStartupIndexer {

    private static final Logger log = LoggerFactory.getLogger(RagStartupIndexer.class);

    private final KnowledgeIngestionService ingestion;
    private final KnowledgeRetrievalService retrieval;
    private final LocalLlmClient llm;
    private final boolean enabled;
    private final Duration maxWait;
    private volatile String state = "pendiente";

    public RagStartupIndexer(KnowledgeIngestionService ingestion, KnowledgeRetrievalService retrieval, LocalLlmClient llm,
            @Value("${sportacus.rag.auto-index:true}") boolean enabled,
            @Value("${sportacus.rag.startup-wait-minutes:15}") long maxWaitMinutes) {
        this.ingestion = ingestion;
        this.retrieval = retrieval;
        this.llm = llm;
        this.enabled = enabled;
        this.maxWait = Duration.ofMinutes(maxWaitMinutes);
    }

    /** Estado de la indexación automática, para GET /api/chat/status. */
    public String state() {
        return state;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled) {
            state = "desactivada";
            return;
        }
        Thread worker = new Thread(this::run, "rag-indexer");
        worker.setDaemon(true);
        worker.start();
    }

    private void run() {
        try {
            if (!retrieval.isAvailable()) {
                state = "sin_pgvector";
                log.warn("Memoria del asistente desactivada: falta pgvector (usa el Postgres de docker-compose, docs/24)");
                return;
            }
            state = "esperando_ollama";
            Instant deadline = Instant.now().plus(maxWait);
            LocalLlmClient.Status status = llm.status();
            while (!status.ready() && Instant.now().isBefore(deadline)) {
                Thread.sleep(15_000);
                status = llm.status();
            }
            if (!status.reachable() || !status.embeddingModelReady()) {
                state = "sin_ollama";
                log.warn("Ollama no está listo ({}): la memoria del asistente se indexará con POST /api/admin/rag/reindex", status);
                return;
            }
            state = "indexando";
            ingestion.reindex(false);
            state = "lista";
            if (status.chatModelReady()) llm.warmUp();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            state = "error";
            log.warn("No se pudo indexar la memoria del asistente: {}", e.toString());
        }
    }
}
