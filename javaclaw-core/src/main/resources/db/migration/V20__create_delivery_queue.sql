-- Delivery queue for external channel notifications (Telegram, Discord).
-- Web Chat uses SSE push directly and bypasses this queue.
-- Multi-pod: claimed_by + claimed_at prevent duplicate processing via SELECT ... FOR UPDATE SKIP LOCKED.
CREATE TABLE delivery_queue (
    id              VARCHAR(256) PRIMARY KEY,
    task_id         VARCHAR(256) REFERENCES tasks(id) ON DELETE SET NULL,
    conversation_id VARCHAR(256) NOT NULL,
    channel_name    VARCHAR(64)  NOT NULL,
    message         TEXT         NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'pending',
    attempts        INT          NOT NULL DEFAULT 0,
    max_attempts    INT          NOT NULL DEFAULT 3,
    error_message   TEXT,
    claimed_by      VARCHAR(64),
    claimed_at      TIMESTAMP,
    next_retry_at   TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    completed_at    TIMESTAMP
);

CREATE INDEX idx_delivery_queue_status ON delivery_queue (status);
CREATE INDEX idx_delivery_queue_retry ON delivery_queue (status, next_retry_at) WHERE status = 'pending';
CREATE INDEX idx_delivery_queue_task ON delivery_queue (task_id);
CREATE INDEX idx_delivery_queue_conv ON delivery_queue (conversation_id);
