-- Gap-closing: conversation continuity + notification center.

CREATE TABLE IF NOT EXISTS conversation_turn (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT    NOT NULL,
    role       TEXT    NOT NULL,
    content    TEXT,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification (
    id         TEXT PRIMARY KEY,
    type       TEXT,
    title      TEXT    NOT NULL,
    body       TEXT,
    source     TEXT,
    read       INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_turn_session ON conversation_turn (session_id, created_at);
CREATE INDEX IF NOT EXISTS idx_notification_read ON notification (read, created_at);
