-- Jarvis Core schema, baseline (Phases 1–3).
-- SQLite is dynamically typed; declared affinities are advisory.

CREATE TABLE IF NOT EXISTS audit_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp   TIMESTAMP,
    input_type  TEXT,
    command     TEXT,
    input       TEXT,
    status      TEXT,
    detail      TEXT
);

CREATE TABLE IF NOT EXISTS memory (
    id          TEXT PRIMARY KEY,
    category    TEXT    NOT NULL,
    title       TEXT    NOT NULL,
    content     TEXT    NOT NULL,
    source      TEXT,
    confidence  REAL,
    visibility  TEXT,
    sensitivity TEXT,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    expires_at  TIMESTAMP,
    enabled     INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_memory_category ON memory (category);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_log (timestamp);
