package com.jarvis.observability;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;

/** A durable record of one Brain/agent run for the Agent Debugger (spec §13.2). */
@Entity
@Table(name = "agent_run")
@Getter
public class AgentRunRecord {

    @Id
    private String id;

    @Column(name = "task_id")
    private String taskId;

    @Column(name = "session_id")
    private String sessionId;

    private String agent;
    private String model;

    @Column(length = 4000)
    private String request;

    @Column(length = 8000)
    private String answer;

    private String status;

    @Column(name = "prompt_tokens")
    private int promptTokens;

    @Column(name = "completion_tokens")
    private int completionTokens;

    private double cost;

    @Column(name = "duration_ms")
    private long durationMs;

    @Column(name = "steps_json", length = 20000)
    private String stepsJson;

    @Column(name = "created_at")
    private Instant createdAt;

    protected AgentRunRecord() {
        // for JPA
    }

    public AgentRunRecord(String id, String taskId, String sessionId, String agent, String model,
                          String request, String answer, String status, int promptTokens, int completionTokens,
                          double cost, long durationMs, String stepsJson) {
        this.id = id;
        this.taskId = taskId;
        this.sessionId = sessionId;
        this.agent = agent;
        this.model = model;
        this.request = request;
        this.answer = answer;
        this.status = status;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.cost = cost;
        this.durationMs = durationMs;
        this.stepsJson = stepsJson;
        this.createdAt = Instant.now();
    }
}
