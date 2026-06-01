package com.jarvis.workflow;

/** How a workflow starts (spec §12). FILE_CHANGED / EMAIL_RECEIVED are future triggers. */
public enum TriggerType {
    MANUAL,
    SCHEDULE,
    WEBHOOK
}
