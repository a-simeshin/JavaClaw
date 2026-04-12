CREATE TABLE conversation_channel_context (
    conversation_id VARCHAR(256) PRIMARY KEY,
    channel_name    VARCHAR(100) NOT NULL,
    routing_data    TEXT,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
