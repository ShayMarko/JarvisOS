package com.jarvis.workflow;

/** The recorded outcome of a step within a run (the durable progress timeline). */
public record StepResult(String stepId, String name, StepType type, StepStatus status, String output, int attempts) {

    public static StepResult pending(WorkflowStep step) {
        return new StepResult(step.id(), step.name(), step.type(), StepStatus.PENDING, null, 0);
    }

    public StepResult as(StepStatus newStatus, String newOutput, int newAttempts) {
        return new StepResult(stepId, name, type, newStatus, newOutput, newAttempts);
    }
}
