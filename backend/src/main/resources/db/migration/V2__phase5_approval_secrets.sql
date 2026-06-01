-- Phase 5: Approval Center + Secrets Vault.

CREATE TABLE IF NOT EXISTS approval_request (
    id             TEXT PRIMARY KEY,
    action_type    TEXT    NOT NULL,
    title          TEXT    NOT NULL,
    description    TEXT,
    risk_level     TEXT    NOT NULL,
    preview        TEXT,
    status         TEXT    NOT NULL,
    created_at     TIMESTAMP,
    decided_at     TIMESTAMP,
    result_summary TEXT
);

CREATE TABLE IF NOT EXISTS secret (
    id               TEXT PRIMARY KEY,
    name             TEXT    NOT NULL,
    connector        TEXT,
    encrypted_value  TEXT    NOT NULL,
    hint             TEXT,
    scopes           TEXT,
    created_at       TIMESTAMP,
    last_accessed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_approval_status ON approval_request (status);
CREATE INDEX IF NOT EXISTS idx_secret_name ON secret (name);
