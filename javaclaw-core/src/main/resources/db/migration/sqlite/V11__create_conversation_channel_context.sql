CREATE TABLE conversation_channel_context (
    conversation_id TEXT PRIMARY KEY,
    channel_name    TEXT NOT NULL,
    routing_data    TEXT,
    updated_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
