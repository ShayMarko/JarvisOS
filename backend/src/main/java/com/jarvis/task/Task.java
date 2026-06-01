package com.jarvis.task;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A unit of work the Brain handled — the durable Task History (spec §10). */
@Entity
@Table(name = "task")
public class Task {

    @Id
    private String id;

    @Column(nullable = false, length = 2000)
    private String request;

    private String agent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

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

    public String getId() { return id; }
    public String getRequest() { return request; }
    public String getAgent() { return agent; }
    public void setAgent(String agent) { this.agent = agent; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
