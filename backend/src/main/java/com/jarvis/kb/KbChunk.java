package com.jarvis.kb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;

/** A chunk of a document plus its embedding (stored as a JSON float array). */
@Entity
@Table(name = "kb_chunk")
@Getter
public class KbChunk {

    @Id
    private String id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    private int ordinal;

    @Column(nullable = false, length = 8000)
    private String content;

    @Column(nullable = false, length = 20000)
    private String embedding;

    protected KbChunk() {
        // for JPA
    }

    public KbChunk(String id, String documentId, int ordinal, String content, String embedding) {
        this.id = id;
        this.documentId = documentId;
        this.ordinal = ordinal;
        this.content = content;
        this.embedding = embedding;
    }
}
