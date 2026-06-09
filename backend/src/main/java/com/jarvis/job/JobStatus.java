package com.jarvis.job;

/** Lifecycle of a background job. */
public enum JobStatus {
    QUEUED,
    RUNNING,
    DONE,
    FAILED,
    CANCELLED
}
