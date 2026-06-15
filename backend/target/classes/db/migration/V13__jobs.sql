-- Background job queue: long agentic requests run detached (build an app, deep research, a newsletter),
-- notify on completion, and are cancellable. Foundational for autonomous / headless-first work.
CREATE TABLE job (
    id          TEXT PRIMARY KEY,        -- "job_xxxxxxxx"
    request     TEXT NOT NULL,           -- the prompt/task to run
    session_id  TEXT,                    -- conversation session it belongs to
    status      TEXT NOT NULL,           -- QUEUED | RUNNING | DONE | FAILED | CANCELLED
    agent       TEXT,                    -- agent that handled it (set on completion)
    model       TEXT,                    -- model used (set on completion)
    result      TEXT,                    -- the answer (length-capped in the entity)
    error       TEXT,                    -- failure reason, if any
    tokens      INTEGER DEFAULT 0,
    source      TEXT,                    -- who submitted it: "api" | "agent" | "command" | ...
    created_at  TIMESTAMP,
    started_at  TIMESTAMP,
    finished_at TIMESTAMP
);
CREATE INDEX idx_job_status ON job (status);
CREATE INDEX idx_job_created ON job (created_at);
