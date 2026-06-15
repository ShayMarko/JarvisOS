-- Phase 8: Workflows + durable runs.

CREATE TABLE IF NOT EXISTS workflow (
    id           TEXT PRIMARY KEY,
    name         TEXT    NOT NULL,
    description  TEXT,
    trigger_type TEXT    NOT NULL,
    cron         TEXT,
    enabled      INTEGER NOT NULL DEFAULT 1,
    steps_json   TEXT    NOT NULL,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workflow_run (
    id           TEXT PRIMARY KEY,
    workflow_id  TEXT    NOT NULL,
    status       TEXT    NOT NULL,
    current_step INTEGER NOT NULL DEFAULT 0,
    trigger      TEXT,
    started_at   TIMESTAMP,
    finished_at  TIMESTAMP,
    results_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_run_workflow ON workflow_run (workflow_id);
CREATE INDEX IF NOT EXISTS idx_run_status ON workflow_run (status);
