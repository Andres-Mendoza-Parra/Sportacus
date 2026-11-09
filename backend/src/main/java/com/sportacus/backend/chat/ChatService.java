package com.sportacus.backend.chat;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sportacus.backend.chat.dto.ChatRequest;
import com.sportacus.backend.chat.dto.ChatResponse;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.rag.EmbeddingClient;
import com.sportacus.backend.rag.KnowledgeChunk;
import com.sportacus.backend.rag.KnowledgeRetrievalService;
import com.sportacus.backend.rag.LocalLlmClient;
import com.sportacus.backend.user.User;
import com.sportacus.backend.world.core.PlanView;
import com.sportacus.backend.world.core.WorldContext;
import com.sportacus.backend.world.core.WorldRegistry;
import com.sportacus.backend.world.enrollment.UserWorldRepository;
import com.sportacus.backend.world.enrollment.WorldContextService;
import com.sportacus.backend.world.plan.PlanService;

/**
 * Asistente RAG 100% local, especializado por mundo: cada mundo aporta su "persona" (prioridades y
 * límites de su nicho) y el backend arma el contexto del usuario (mundo, ruta, salud, próxima sesión)
 * sin depender del frontend. Recupera primero el conocimiento del mundo activo.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String SYSTEM_PROMPT = """
            Eres el asistente de Sportacus, una app que guía a cualquier persona en su mundo de actividad física.
            Respondes siempre en español neutro, breve y concreto (máximo un par de párrafos o una lista corta).

            Usa el "Conocimiento del catálogo" como fuente principal; si no hay información suficiente, dilo en vez
            de inventar. Personaliza con el "Contexto del usuario". Nunca des diagnósticos médicos. Ante dolor agudo,
            señales de alarma o lesión, recomienda consultar a un profesional de la salud.
            Si el usuario pide un plan nuevo explícitamente, añade al final <accion>{"tipo":"regenerar_plan"}</accion>.
            """;

    private final EmbeddingClient embeddingClient;
    private final KnowledgeRetrievalService retrievalService;
    private final LocalLlmClient llmClient;
    private final WorldContextService contexts;
    private final UserWorldRepository userWorlds;
    private final WorldRegistry registry;
    private final PlanService plans;
    private final int topK;

    public ChatService(EmbeddingClient embeddingClient, KnowledgeRetrievalService retrievalService,
            LocalLlmClient llmClient, WorldContextService contexts, UserWorldRepository userWorlds,
            WorldRegistry registry, PlanService plans, @Value("${sportacus.rag.top-k}") int topK) {
        this.embeddingClient = embeddingClient;
        this.retrievalService = retrievalService;
        this.llmClient = llmClient;
        this.contexts = contexts;
        this.userWorlds = userWorlds;
        this.registry = registry;
        this.plans = plans;
        this.topK = topK;
    }

    public ChatResponse answer(User user, ChatRequest request) {
        WorldContext ctx = userWorlds.findActive(user.getId()).isPresent() ? contexts.resolve(user, null) : null;
        String worldKey = ctx == null ? null : ctx.world().worldKey();
        List<KnowledgeChunk> chunks = List.of();
        if (retrievalService.isAvailable()) {
            try {
                chunks = retrievalService.search(embeddingClient.embed(request.message()), worldKey, topK);
            } catch (RuntimeException e) {
                log.debug("Sin recuperación RAG: {}", e.toString());
            }
        }
        boolean personalized = Boolean.TRUE.equals(request.includeProfile());
        String reply = llmClient.generate(buildPrompt(request, ctx, chunks, personalized));
        return new ChatResponse(reply, personalized && ctx != null);
    }

    private String buildPrompt(ChatRequest request, WorldContext ctx, List<KnowledgeChunk> chunks,
            boolean personalized) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT).append('\n');
        if (ctx != null) {
            sb.append("\n## Especialidad del mundo activo\n")
                    .append(registry.require(ctx.world().worldKey()).assistantPersona(ctx)).append('\n');
        }
        if (!chunks.isEmpty()) {
            sb.append("\n## Conocimiento del catálogo\n");
            for (KnowledgeChunk chunk : chunks) sb.append("- ").append(chunk.content()).append('\n');
        }
        if (personalized && ctx != null) {
            sb.append("\n## Contexto del usuario\n").append(userContext(ctx));
            if (request.context() != null && !request.context().isBlank()) {
                sb.append(request.context()).append('\n');
            }
        }
        sb.append("\nPregunta del usuario: ").append(request.message());
        return sb.toString();
    }

    private String userContext(WorldContext ctx) {
        StringBuilder sb = new StringBuilder();
        var p = ctx.user().profile();
        sb.append("- Nombre: ").append(ctx.user().displayName()).append('\n');
        if (p.age() != null) sb.append("- Edad: ").append(p.age()).append(" años\n");
        sb.append("- Mundo: ").append(ctx.world().worldKey()).append(", ruta ").append(ctx.world().trackKey())
                .append(", nivel ").append(ctx.world().level()).append(" de 5\n");
        HealthSummary h = ctx.user().health();
        if (!h.conditions().isEmpty()) {
            sb.append("- Condiciones/lesiones activas: ")
                    .append(String.join(", ", h.conditions().stream().map(c -> c.label()).toList())).append('\n');
        }
        if (!h.vigorousAllowed()) sb.append("- Debe mantener intensidad moderada hasta tener aval médico\n");
        try {
            PlanView plan = plans.ensurePlan(ctx);
            var next = plan.nextSession();
            sb.append("- Plan: ").append(plan.title()).append('\n');
            if (next != null) {
                sb.append("- Próxima sesión: ").append(next.title()).append(" (")
                        .append(String.join(", ", next.blocks().stream().map(b -> b.title()).toList())).append(")\n");
            }
        } catch (RuntimeException e) {
            log.debug("Sin plan para el contexto del chat: {}", e.toString());
        }
        return sb.toString();
    }
}
