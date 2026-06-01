package com.jarvis.conversation;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A single chat turn, persisted per session so the Brain has conversation context (#8). */
@Entity
@Table(name = "conversation_turn")
public class ConversationTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    /** USER or ASSISTANT. */
    @Column(nullable = false)
    private String role;

    @Column(length = 8000)
    private String content;

    @Column(name = "created_at")
    private Instant createdAt;

    protected ConversationTurn() {
        // for JPA
    }

    public ConversationTurn(String sessionId, String role, String content) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
