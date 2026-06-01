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

/** A defined automation (spec §12). Steps are stored as JSON in {@code stepsJson}. */
@Entity
@Table(name = "workflow")
@Getter
public class Workflow {

    @Id
    private String id;

    @Setter
    @Column(nullable = false)
    private String name;

    @Setter
    private String description;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private TriggerType triggerType;

    /** Cron expression when triggerType == SCHEDULE (Spring 6-field cron). */
    @Setter
    private String cron;

    @Setter
    private boolean enabled;

    @Setter
    @Column(name = "steps_json", nullable = false, length = 100000)
    private String stepsJson;

    @Column(name = "created_at")
    private Instant createdAt;

    @Setter
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
}
