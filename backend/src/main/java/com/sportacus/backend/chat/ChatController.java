package com.sportacus.backend.chat;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sportacus.backend.chat.dto.ChatRequest;
import com.sportacus.backend.chat.dto.ChatResponse;
import com.sportacus.backend.rag.KnowledgeRetrievalService;
import com.sportacus.backend.rag.LocalLlmClient;
import com.sportacus.backend.rag.RagStartupIndexer;
import com.sportacus.backend.user.User;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final LocalLlmClient llm;
    private final KnowledgeRetrievalService retrieval;
    private final RagStartupIndexer indexer;

    public ChatController(ChatService chatService, LocalLlmClient llm, KnowledgeRetrievalService retrieval,
            RagStartupIndexer indexer) {
        this.chatService = chatService;
        this.llm = llm;
        this.retrieval = retrieval;
        this.indexer = indexer;
    }

    @PostMapping
    public ChatResponse ask(@AuthenticationPrincipal User currentUser, @Valid @RequestBody ChatRequest request) {
        return chatService.answer(currentUser, request);
    }

    /**
     * Estado del asistente para la app: available = puede responder; knowledgeChunks = tamaño de su memoria;
     * indexState = pendiente | esperando_ollama | indexando | lista | sin_ollama | sin_pgvector | error | desactivada.
     */
    public record Status(boolean available, String model, long knowledgeChunks, String indexState) {
    }

    @GetMapping("/status")
    public Status status() {
        LocalLlmClient.Status s = llm.status();
        return new Status(s.reachable() && s.chatModelReady(), s.chatModel(), retrieval.count(), indexer.state());
    }
}
