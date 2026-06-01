package com.jarvis.workflow;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A durable execution of a workflow (spec §12.1). State (status, current step,
 * per-step results) is persisted after every step, so a run survives restarts
 * and an approval-gated run can be resumed.
 */
@Entity
@Table(name = "workflow_run")
public class WorkflowRun {

    @Id
    private String id;

    @Column(name = "workflow_id", nullable = false)
    private String workflowId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    @Column(name = "current_step", nullable = false)
    private int currentStep;

    private String trigger;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

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

    public String getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }
    public int getCurrentStep() { return currentStep; }
    public void setCurrentStep(int currentStep) { this.currentStep = currentStep; }
    public String getTrigger() { return trigger; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public String getResultsJson() { return resultsJson; }
    public void setResultsJson(String resultsJson) { this.resultsJson = resultsJson; }
}
