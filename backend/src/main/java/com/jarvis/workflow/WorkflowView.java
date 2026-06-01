package com.jarvis.workflow;

import java.time.Instant;
import java.util.List;

/** API/UI projection of a workflow with its steps parsed. */
public record WorkflowView(
        String id,
        String name,
        String description,
        TriggerType triggerType,
        String cron,
        boolean enabled,
        boolean scheduled,
        List<WorkflowStep> steps,
        Instant createdAt
) {}
