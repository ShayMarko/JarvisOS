package com.jarvis.notification;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/** A user-facing notification (spec §8 Notification Capability / center). */
@Entity
@Table(name = "notification")
@Getter
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

    /**
     * Optional id of an actionable target this notification is about — e.g. an ApprovalRequest id when
     * {@code source == "approval"}. Lets the client render inline Approve/Decline buttons. Null otherwise.
     */
    @Column(name = "action_id")
    private String actionId;

    @Setter
    private boolean read;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Notification() {
        // for JPA
    }

    public Notification(String id, String type, String title, String body, String source) {
        this(id, type, title, body, source, null);
    }

    public Notification(String id, String type, String title, String body, String source, String actionId) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.body = body;
        this.source = source;
        this.actionId = actionId;
        this.read = false;
        this.createdAt = Instant.now();
    }
}
