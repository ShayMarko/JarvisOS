package com.jarvis.kb;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;

/** An indexed document in the Knowledge Base (spec §10.2). */
@Entity
@Table(name = "kb_document")
@Getter
public class KbDocument {

    @Id
    private String id;

    /** Where it came from — an Explorer path or "manual:<title>". */
    @Column(nullable = false)
    private String source;

    private String title;

    @Column(name = "chunk_count")
    private int chunkCount;

    @Column(name = "created_at")
    private Instant createdAt;

    protected KbDocument() {
        // for JPA
    }

    public KbDocument(String id, String source, String title, int chunkCount) {
        this.id = id;
        this.source = source;
        this.title = title;
        this.chunkCount = chunkCount;
        this.createdAt = Instant.now();
    }
}
