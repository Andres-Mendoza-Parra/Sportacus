package com.sportacus.backend.legal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.user.User;

@RestController
@RequestMapping("/api/legal")
public class LegalController {

    private final LegalService legal;

    public LegalController(LegalService legal) {
        this.legal = legal;
    }

    /** Público (sin token): la app lo muestra antes de crear la cuenta. */
    @GetMapping("/documents")
    public List<LegalService.LegalDocument> documents() {
        return legal.documents();
    }

    public record AcceptRequest(String document, String version) {
    }

    @PostMapping("/accept")
    public LegalService.LegalStatus accept(@AuthenticationPrincipal User user, @RequestBody AcceptRequest body) {
        if (body == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el documento");
        return legal.accept(user.getId(), body.document(), body.version());
    }
}
