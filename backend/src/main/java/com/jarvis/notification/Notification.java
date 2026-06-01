package com.jarvis.notification;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A user-facing notification (spec §8 Notification Capability / center). */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    private String id;

    /** info | success | warning | error. */
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String body;

    /** Where it came from: workflow, approval, system, … */
    private String source;

    private boolean read;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Notification() {
        // for JPA
    }

    public Notification(String id, String type, String title, String body, String source) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.body = body;
        this.source = source;
        this.read = false;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getSource() { return source; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public Instant getCreatedAt() { return createdAt; }
}
