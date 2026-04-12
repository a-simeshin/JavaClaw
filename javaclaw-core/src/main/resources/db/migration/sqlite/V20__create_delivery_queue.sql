-- Delivery queue for external channel notifications (Telegram, Discord).
-- Web Chat uses SSE push directly and bypasses this queue.
-- Multi-pod coordination uses claimed_by + claimed_at. SQLite is single-writer,
-- so the SELECT ... FOR UPDATE SKIP LOCKED pattern is emulated at the query layer.
CREATE TABLE delivery_queue (
    id              TEXT PRIMARY KEY,
    task_id         TEXT REFERENCES tasks(id) ON DELETE SET NULL,
    conversation_id TEXT NOT NULL,
    channel_name    TEXT NOT NULL,
    message         TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'pending',
    attempts        INTEGER NOT NULL DEFAULT 0,
    max_attempts    INTEGER NOT NULL DEFAULT 3,
    error_message   TEXT,
    claimed_by      TEXT,
    claimed_at      TEXT,
    next_retry_at   TEXT,
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    completed_at    TEXT
);

CREATE INDEX idx_delivery_queue_status ON delivery_queue (status);
CREATE INDEX idx_delivery_queue_retry ON delivery_queue (status, next_retry_at) WHERE status = 'pending';
CREATE INDEX idx_delivery_queue_task ON delivery_queue (task_id);
CREATE INDEX idx_delivery_queue_conv ON delivery_queue (conversation_id);
