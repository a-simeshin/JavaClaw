CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(256) NOT NULL,
    content         TEXT NOT NULL,
    type            VARCHAR(100) NOT NULL,
    "timestamp"     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_spring_ai_chat_memory_conversation_id
    ON SPRING_AI_CHAT_MEMORY (conversation_id);
