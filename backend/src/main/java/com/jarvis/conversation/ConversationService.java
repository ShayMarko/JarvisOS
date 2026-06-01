package com.jarvis.conversation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.jarvis.config.JarvisLimitsProperties;

import lombok.RequiredArgsConstructor;

/** Persists and recalls per-session chat turns for conversation continuity (#8). */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository repository;
    private final JarvisLimitsProperties limits;

    public void record(String sessionId, String role, String content) {
        repository.save(new ConversationTurn(sessionId, role, content));
    }

    /** Recent turns for a session, oldest-first (ready to prepend to a prompt). */
    public List<ConversationTurn> recent(String sessionId) {
        List<ConversationTurn> desc = repository.findBySessionIdOrderByCreatedAtDesc(
                sessionId, PageRequest.of(0, limits.getConversationHistoryTurns()));
        List<ConversationTurn> chronological = new ArrayList<>(desc);
        Collections.reverse(chronological);
        return chronological;
    }
}
