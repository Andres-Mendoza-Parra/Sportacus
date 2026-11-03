package com.sportacus.backend.chat;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sportacus.backend.chat.dto.ChatRequest;
import com.sportacus.backend.chat.dto.ChatResponse;
import com.sportacus.backend.user.User;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse ask(@AuthenticationPrincipal User currentUser, @Valid @RequestBody ChatRequest request) {
        return chatService.answer(currentUser, request);
    }
}
