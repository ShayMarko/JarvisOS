package com.jarvis.memory;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import lombok.Getter;
import lombok.Setter;

/**
 * A single item of long-term memory about the user (spec §10.1, Appendix C).
 * The user owns this data fully — it is viewable, editable and deletable.
 */
@Entity
@Table(name = "memory")
@Getter
@Setter
public class Memory {

    @Id
    private String id;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 8000)
    private String content;

    /** Provenance: where this memory came from (chat, manual, file, …). */
    private String source;

    /** 0.0–1.0 confidence in the memory. */
    private double confidence;

    @Enumerated(EnumType.STRING)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    private Sensitivity sensitivity;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Optional expiry for temporary memory; null = never expires. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    private boolean enabled;

    protected Memory() {
        // for JPA
    }

    /** A memory is active when enabled and not past its expiry. */
    @Transient
    public boolean isActive() {
        return enabled && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }
}
