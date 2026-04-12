-- Pre-V43 schema of SPRING_AI_CHAT_MEMORY for SQLite.
-- V43 will rebuild the table: add id INTEGER PRIMARY KEY AUTOINCREMENT, rename timestamp -> created_at TEXT.
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    content         TEXT,
    type            TEXT NOT NULL,
    timestamp       INTEGER NOT NULL DEFAULT 0,
    CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL'))
);
