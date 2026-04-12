-- V5: Conversations metadata + FK chat_memory -> conversations.
--
-- SQLite does not support ALTER TABLE ADD CONSTRAINT FOREIGN KEY. To achieve
-- parity with the Postgres `ON DELETE CASCADE` FK from spring_ai_chat_memory,
-- we rebuild the chat memory table after creating `conversations`, baking the
-- FK into the new CREATE. Enforcement requires `PRAGMA foreign_keys = ON`,
-- which is configured on the sqlite DataSource at the application layer.
--
-- The rebuild must preserve the existing schema shape from javaclaw-memory V2
-- (post-squash): surrogate id INTEGER PK AUTOINCREMENT, nullable content, CHECK
-- on type, and ISO-8601 TEXT created_at. The id column is copied verbatim so
-- previously inserted rows retain their primary-key values.
CREATE TABLE conversations (
    id         TEXT PRIMARY KEY,
    user_id    TEXT REFERENCES users(id) ON DELETE SET NULL,
    title      TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_conversations_user_id    ON conversations(user_id);
CREATE INDEX idx_conversations_created_at ON conversations(created_at);

-- Rebuild spring_ai_chat_memory with inline FK (ON DELETE CASCADE).
CREATE TABLE SPRING_AI_CHAT_MEMORY_NEW (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id TEXT NOT NULL,
    content         TEXT,
    type            TEXT NOT NULL,
    created_at      TEXT NOT NULL,
    CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
);

INSERT INTO SPRING_AI_CHAT_MEMORY_NEW (id, conversation_id, content, type, created_at)
SELECT id, conversation_id, content, type, created_at FROM SPRING_AI_CHAT_MEMORY;

DROP TABLE SPRING_AI_CHAT_MEMORY;
ALTER TABLE SPRING_AI_CHAT_MEMORY_NEW RENAME TO SPRING_AI_CHAT_MEMORY;

CREATE INDEX IF NOT EXISTS idx_spring_ai_chat_memory_conversation_id_created_at
    ON SPRING_AI_CHAT_MEMORY(conversation_id, created_at);
