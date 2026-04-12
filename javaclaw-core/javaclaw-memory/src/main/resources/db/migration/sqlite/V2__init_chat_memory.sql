-- V2: Chat memory persistence schema owned by the javaclaw-memory module (SQLite).
--
-- Backs Spring Data JDBC's SpringDataChatMemoryRepository with the same final
-- layout as the PostgreSQL counterpart: surrogate INTEGER PRIMARY KEY AUTOINCREMENT,
-- nullable content (tool-response rows), and an ISO-8601 TEXT created_at column.
-- SQLite's datetime functions order lexicographically over ISO-8601 TEXT, so no
-- numeric/epoch representation is needed.
--
-- The CHECK constraint on `type` enforces the same set of message kinds Spring AI
-- emits. A foreign key from conversation_id to conversations(id) is added later by
-- javaclaw-core V5, which rebuilds this table (SQLite has no ALTER TABLE ADD
-- CONSTRAINT FOREIGN KEY) while carrying the existing id column forward.
CREATE TABLE SPRING_AI_CHAT_MEMORY (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id TEXT NOT NULL,
    content         TEXT,
    type            TEXT NOT NULL,
    created_at      TEXT NOT NULL,
    CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL'))
);

CREATE INDEX IF NOT EXISTS idx_spring_ai_chat_memory_conversation_id_created_at
    ON SPRING_AI_CHAT_MEMORY(conversation_id, created_at);
