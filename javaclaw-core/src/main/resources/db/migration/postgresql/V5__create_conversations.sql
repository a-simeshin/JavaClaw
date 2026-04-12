-- V5: Conversations metadata + FK from SPRING_AI_CHAT_MEMORY
CREATE TABLE conversations (
    id         VARCHAR(256) PRIMARY KEY,
    user_id    VARCHAR(36)  REFERENCES users(id) ON DELETE SET NULL,
    title      VARCHAR(255),
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversations_user_id    ON conversations(user_id);
CREATE INDEX idx_conversations_created_at ON conversations(created_at);
ALTER TABLE spring_ai_chat_memory
    ADD CONSTRAINT fk_chat_memory_conversation_id
    FOREIGN KEY (conversation_id)
    REFERENCES conversations(id)
    ON DELETE CASCADE
    DEFERRABLE INITIALLY DEFERRED;
