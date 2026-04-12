-- Minimal conversations table for javaclaw-memory integration tests.
-- Only the id column is needed; the FK from SPRING_AI_CHAT_MEMORY references it.
CREATE TABLE conversations (
    id VARCHAR(256) PRIMARY KEY
);
