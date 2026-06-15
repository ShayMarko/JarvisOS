-- One-shot reminders / deferred tasks: fire once at an absolute time. The model resolves a relative
-- phrase ("in an hour", "next Tuesday 3pm") to fire_at using the current time injected into its prompt.
CREATE TABLE reminder (
    id         TEXT PRIMARY KEY,
    message    TEXT NOT NULL,
    fire_at    TIMESTAMP NOT NULL,
    run_task   BOOLEAN   NOT NULL DEFAULT 0,   -- 0 = just notify; 1 = run the message through the Brain
    fired      BOOLEAN   NOT NULL DEFAULT 0,
    source     TEXT,
    created_at TIMESTAMP NOT NULL,
    fired_at   TIMESTAMP
);
CREATE INDEX idx_reminder_due ON reminder (fired, fire_at);
