package com.jarvis.job;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * A detached, long-running Brain request — submitted, run on a background worker, and reported on completion
 * (Discord/bell). Lets Jarvis offload big work (build an app, deep research, write a newsletter) without
 * blocking the caller, which matters most for the headless / voice / phone-first flows.
 */
@Entity
@Table(name = "job")
@Getter
public class Job {

    @Id
    private String id;

    @Column(nullable = false, length = 4000)
    private String request;

    @Column(name = "session_id")
    private String sessionId;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Setter
    private String agent;

    @Setter
    private String model;

    @Setter
    @Column(length = 8000)
    private String result;

    @Setter
    @Column(length = 4000)
    private String error;

    @Setter
    private int tokens;

    private String source;

    @Column(name = "created_at")
    private Instant createdAt;

    @Setter
    @Column(name = "started_at")
    private Instant startedAt;

    @Setter
    @Column(name = "finished_at")
    private Instant finishedAt;

    protected Job() {
        // for JPA
    }

    public Job(String id, String request, String sessionId, String source) {
        this.id = id;
        this.request = request;
        this.sessionId = sessionId;
        this.source = source;
        this.status = JobStatus.QUEUED;
        this.createdAt = Instant.now();
    }
}
