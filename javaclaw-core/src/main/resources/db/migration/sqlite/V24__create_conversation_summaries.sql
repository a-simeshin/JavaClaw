-- V24: Conversation summaries for context window management (8.3)
-- Stores LLM-generated summaries of older turns that were dropped during windowing.
CREATE TABLE IF NOT EXISTS conversation_summaries (
    id                TEXT    PRIMARY KEY,
    conversation_id   TEXT    NOT NULL,
    summary_text      TEXT    NOT NULL,
    messages_covered  INTEGER NOT NULL DEFAULT 0,
    created_at        TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_conv_summaries_conv_id ON conversation_summaries(conversation_id);

-- COMMENT ON TABLE is not supported in SQLite; the Postgres migration keeps the documentation.
