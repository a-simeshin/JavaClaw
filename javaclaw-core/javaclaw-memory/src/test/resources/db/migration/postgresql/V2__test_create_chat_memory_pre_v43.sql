-- Pre-V43 schema of SPRING_AI_CHAT_MEMORY (no id column, timestamp column present).
-- V43 will ADD COLUMN id, ADD PRIMARY KEY, and RENAME COLUMN timestamp -> created_at.
CREATE TABLE SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(256) NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    content         TEXT,
    type            VARCHAR(100) NOT NULL,
    "timestamp"     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_spring_ai_chat_memory_conversation_id
    ON SPRING_AI_CHAT_MEMORY (conversation_id);
