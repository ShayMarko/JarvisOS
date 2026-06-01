package com.jarvis.task;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/** A unit of work the Brain handled — the durable Task History (spec §10). */
@Entity
@Table(name = "task")
@Getter
public class Task {

    @Id
    private String id;

    @Column(nullable = false, length = 2000)
    private String request;

    @Setter
    private String agent;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Setter
    @Column(name = "finished_at")
    private Instant finishedAt;

    @Setter
    @Column(length = 4000)
    private String summary;

    protected Task() {
        // for JPA
    }

    public Task(String id, String request) {
        this.id = id;
        this.request = request;
        this.status = TaskStatus.RUNNING;
        this.createdAt = Instant.now();
    }
}
