-- Phase 10: agent-run observability (the Agent Debugger's durable record).

CREATE TABLE IF NOT EXISTS agent_run (
    id                TEXT PRIMARY KEY,
    task_id           TEXT,
    session_id        TEXT,
    agent             TEXT,
    model             TEXT,
    request           TEXT,
    answer            TEXT,
    status            TEXT,
    prompt_tokens     INTEGER,
    completion_tokens INTEGER,
    cost              REAL,
    duration_ms       INTEGER,
    steps_json        TEXT,
    created_at        TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agentrun_created ON agent_run (created_at);
