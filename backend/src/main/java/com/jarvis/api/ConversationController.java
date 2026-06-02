package com.jarvis.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.conversation.ConversationService;
import com.jarvis.conversation.ConversationTurn;

import lombok.RequiredArgsConstructor;

/** Recent chat transcript for a session, so the UI can rehydrate the conversation on reload. */
@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
public class ConversationController {

    public record TurnView(String role, String content, String createdAt) {}

    private final ConversationService conversations;

    @GetMapping
    public List<TurnView> recent(@RequestParam("sessionId") String sessionId) {
        return conversations.recent(sessionId).stream()
                .map(t -> new TurnView(t.getRole(), t.getContent(),
                        t.getCreatedAt() == null ? null : t.getCreatedAt().toString()))
                .toList();
    }
}
