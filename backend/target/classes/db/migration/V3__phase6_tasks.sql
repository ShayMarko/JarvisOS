-- Phase 6: Task history (the Brain records a task per request).

CREATE TABLE IF NOT EXISTS task (
    id          TEXT PRIMARY KEY,
    request     TEXT    NOT NULL,
    agent       TEXT,
    status      TEXT    NOT NULL,
    created_at  TIMESTAMP,
    finished_at TIMESTAMP,
    summary     TEXT
);

CREATE INDEX IF NOT EXISTS idx_task_status ON task (status);
CREATE INDEX IF NOT EXISTS idx_task_created ON task (created_at);
