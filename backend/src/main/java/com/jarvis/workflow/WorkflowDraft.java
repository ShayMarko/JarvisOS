package com.jarvis.workflow;

import java.util.List;

/** Writable definition of a workflow (create/update). */
public record WorkflowDraft(
        String name,
        String description,
        TriggerType triggerType,
        String cron,
        Boolean enabled,
        List<WorkflowStep> steps
) {}
