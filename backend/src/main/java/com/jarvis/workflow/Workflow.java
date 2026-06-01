package com.jarvis.workflow;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A defined automation (spec §12). Steps are stored as JSON in {@code stepsJson}. */
@Entity
@Table(name = "workflow")
public class Workflow {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private TriggerType triggerType;

    /** Cron expression when triggerType == SCHEDULE (Spring 6-field cron). */
    private String cron;

    private boolean enabled;

    @Column(name = "steps_json", nullable = false, length = 100000)
    private String stepsJson;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Workflow() {
        // for JPA
    }

    public Workflow(String id, String name, String description, TriggerType triggerType,
                    String cron, boolean enabled, String stepsJson) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.triggerType = triggerType;
        this.cron = cron;
        this.enabled = enabled;
        this.stepsJson = stepsJson;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public TriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(TriggerType triggerType) { this.triggerType = triggerType; }
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getStepsJson() { return stepsJson; }
    public void setStepsJson(String stepsJson) { this.stepsJson = stepsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
