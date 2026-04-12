-- Shared conversations: allows users to share conversations with other users
CREATE TABLE conversation_shares (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    shared_with     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission      TEXT NOT NULL DEFAULT 'READ',
    shared_by       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),

    CONSTRAINT uq_conversation_share UNIQUE (conversation_id, shared_with)
);

CREATE INDEX idx_conversation_shares_shared_with ON conversation_shares(shared_with);
CREATE INDEX idx_conversation_shares_conversation ON conversation_shares(conversation_id);
