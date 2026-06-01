package com.jarvis.workflow;

/** Lifecycle of a workflow run (spec §12.1 Durable Task Engine). */
public enum RunStatus {
    RUNNING,
    PAUSED,   // awaiting an approval gate
    DONE,
    FAILED
}
