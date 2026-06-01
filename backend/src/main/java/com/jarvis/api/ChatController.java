package com.jarvis.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.brain.ChatResponse;
import com.jarvis.brain.Orchestrator;

import jakarta.validation.constraints.NotBlank;

/** Direct chat entry point to the Jarvis Brain (spec §6). */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final Orchestrator orchestrator;

    public ChatController(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public record ChatRequest(@NotBlank String message, String sessionId) {}

    @PostMapping
    public ChatResponse chat(@jakarta.validation.Valid @RequestBody ChatRequest request) {
        return orchestrator.handle(request.message(),
                request.sessionId() == null ? "default" : request.sessionId());
    }
}
