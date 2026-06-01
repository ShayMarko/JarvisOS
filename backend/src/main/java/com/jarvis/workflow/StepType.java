package com.jarvis.workflow;

/** A workflow step's kind — each maps to a real Jarvis capability (spec §12). */
public enum StepType {
    /** Run a slash command, config {command}. */
    COMMAND,
    /** Ask the Jarvis Brain, config {prompt}. */
    BRAIN,
    /** Invoke a connector action, config {connector, action, args}. */
    CONNECTOR,
    /** Emit a notification/output, config {message}. */
    NOTIFY,
    /** Human-approval gate (pauses the run until approved), config {title, why}. */
    APPROVAL
}
