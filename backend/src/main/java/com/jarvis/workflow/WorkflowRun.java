package com.jarvis.workflow;

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
 * A durable execution of a workflow (spec §12.1). State (status, current step,
 * per-step results) is persisted after every step, so a run survives restarts
 * and an approval-gated run can be resumed.
 */
@Entity
@Table(name = "workflow_run")
@Getter
public class WorkflowRun {

    @Id
    private String id;

    @Column(name = "workflow_id", nullable = false)
    private String workflowId;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    @Setter
    @Column(name = "current_step", nullable = false)
    private int currentStep;

    private String trigger;

    @Column(name = "started_at")
    private Instant startedAt;

    @Setter
    @Column(name = "finished_at")
    private Instant finishedAt;

    @Setter
    @Column(name = "results_json", length = 100000)
    private String resultsJson;

    protected WorkflowRun() {
        // for JPA
    }

    public WorkflowRun(String id, String workflowId, String trigger, String resultsJson) {
        this.id = id;
        this.workflowId = workflowId;
        this.trigger = trigger;
        this.status = RunStatus.RUNNING;
        this.currentStep = 0;
        this.startedAt = Instant.now();
        this.resultsJson = resultsJson;
    }
}
