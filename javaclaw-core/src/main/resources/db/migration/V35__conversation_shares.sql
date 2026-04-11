-- Shared conversations: allows users to share conversations with other users
CREATE TABLE conversation_shares (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    shared_with     VARCHAR(36)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission      VARCHAR(16)  NOT NULL DEFAULT 'READ',
    shared_by       VARCHAR(36)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_conversation_share UNIQUE (conversation_id, shared_with)
);

CREATE INDEX idx_conversation_shares_shared_with ON conversation_shares(shared_with);
CREATE INDEX idx_conversation_shares_conversation ON conversation_shares(conversation_id);
