package com.sportacus.backend.rag;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dispara la (re)indexación del catálogo hacia {@code knowledge_chunk}. Requiere estar
 * autenticado (no hay roles en el proyecto todavía); cuando se agregue un rol de administrador,
 * restringir este endpoint a ese rol.
 */
@RestController
@RequestMapping("/api/admin/rag")
public class RagAdminController {

    private final KnowledgeIngestionService ingestionService;

    public RagAdminController(KnowledgeIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/reindex")
    public Map<String, Integer> reindex() {
        return Map.of("chunksIndexed", ingestionService.reindexAll());
    }
}
