package com.jarvis.workflow;

import java.time.Instant;
import java.util.List;

/** API/UI projection of a run with its step results parsed (the progress timeline). */
public record RunView(
        String id,
        String workflowId,
        RunStatus status,
        int currentStep,
        String trigger,
        Instant startedAt,
        Instant finishedAt,
        List<StepResult> steps
) {}
