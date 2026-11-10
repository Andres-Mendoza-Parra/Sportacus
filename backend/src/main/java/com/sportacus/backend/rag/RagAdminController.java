package com.sportacus.backend.rag;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sportacus.backend.security.AdminAccess;
import com.sportacus.backend.user.User;

/**
 * Reindexación manual de la memoria del asistente (normalmente no hace falta: se pone al día sola al arrancar).
 * Solo para los correos de {@code sportacus.admin-emails}; el resto recibe 403.
 */
@RestController
@RequestMapping("/api/admin/rag")
public class RagAdminController {

    private final KnowledgeIngestionService ingestionService;
    private final AdminAccess adminAccess;

    public RagAdminController(KnowledgeIngestionService ingestionService, AdminAccess adminAccess) {
        this.ingestionService = ingestionService;
        this.adminAccess = adminAccess;
    }

    /** @param force true regenera todos los vectores (por ejemplo, tras cambiar el modelo de embeddings). */
    @PostMapping("/reindex")
    public KnowledgeIngestionService.IndexReport reindex(@AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "false") boolean force) {
        adminAccess.require(user);
        return ingestionService.reindex(force);
    }
}
