-- V24: Conversation summaries for context window management (8.3)
-- Stores LLM-generated summaries of older turns that were dropped during windowing.
CREATE TABLE IF NOT EXISTS conversation_summaries (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id   VARCHAR(256) NOT NULL,
    summary_text      TEXT         NOT NULL,
    messages_covered  INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_conv_summaries_conv_id ON conversation_summaries(conversation_id);

-- Only one active summary per conversation (latest wins)
COMMENT ON TABLE conversation_summaries IS 'LLM-generated summaries of dropped conversation turns for context window management';
