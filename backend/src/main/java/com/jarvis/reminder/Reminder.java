package com.jarvis.reminder;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/** A one-shot reminder / deferred task that fires once at {@link #fireAt}. */
@Entity
@Table(name = "reminder")
@Getter
@Setter
public class Reminder {

    @Id
    private String id;

    @Column(nullable = false)
    private String message;

    @Column(name = "fire_at", nullable = false)
    private Instant fireAt;

    /** false = just notify when due; true = run {@code message} through the Brain when due. */
    @Column(name = "run_task")
    private boolean runTask;

    private boolean fired;

    private String source;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "fired_at")
    private Instant firedAt;

    protected Reminder() {
        // for JPA
    }

    public Reminder(String id, String message, Instant fireAt, boolean runTask, String source) {
        this.id = id;
        this.message = message;
        this.fireAt = fireAt;
        this.runTask = runTask;
        this.source = source;
        this.fired = false;
        this.createdAt = Instant.now();
    }
}
