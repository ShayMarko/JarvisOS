package com.jarvis.conversation;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<ConversationTurn, Long> {

    List<ConversationTurn> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);
}
