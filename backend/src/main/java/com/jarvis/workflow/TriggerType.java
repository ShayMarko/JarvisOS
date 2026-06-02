package com.jarvis.workflow;

/**
 * How a workflow starts (spec §12). The {@code cron} field is overloaded by trigger:
 * SCHEDULE → cron expression; FILE_CHANGED → watched folder (relative to the Jarvis
 * Explorer root); WEBHOOK → fired by {@code POST /api/webhooks/{id}}.
 */
public enum TriggerType {
    MANUAL,
    SCHEDULE,
    WEBHOOK,
    FILE_CHANGED
}
