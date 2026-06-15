-- Self-extending skills: procedures the user teaches Jarvis ("learn to do X") that it can recall
-- and perform later using its existing tools. Agentic, no code compilation.

CREATE TABLE IF NOT EXISTS skill (
    id           TEXT PRIMARY KEY,
    name         TEXT NOT NULL,
    description  TEXT NOT NULL,
    instructions TEXT NOT NULL,
    uses         INTEGER NOT NULL DEFAULT 0,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_skill_name ON skill (name);
