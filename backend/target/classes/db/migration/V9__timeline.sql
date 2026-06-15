-- Episodic timeline: one roll-up row per day ("what happened that day"), queryable later.
CREATE TABLE timeline_entry (
    id         TEXT PRIMARY KEY,
    day        TEXT NOT NULL UNIQUE,   -- ISO yyyy-MM-dd
    summary    TEXT,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_timeline_day ON timeline_entry (day);
