-- Phase 9: Knowledge Base / RAG.

CREATE TABLE IF NOT EXISTS kb_document (
    id          TEXT PRIMARY KEY,
    source      TEXT    NOT NULL,
    title       TEXT,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_chunk (
    id          TEXT PRIMARY KEY,
    document_id TEXT    NOT NULL,
    ordinal     INTEGER NOT NULL,
    content     TEXT    NOT NULL,
    embedding   TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chunk_doc ON kb_chunk (document_id);
CREATE INDEX IF NOT EXISTS idx_doc_source ON kb_document (source);
