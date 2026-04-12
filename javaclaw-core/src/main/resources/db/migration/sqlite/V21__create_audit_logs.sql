-- Task audit log: full execution history for each task lifecycle event
CREATE TABLE task_audit_log (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id          TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    execution_id     TEXT,
    event_type       TEXT NOT NULL,
    created_at       TEXT NOT NULL DEFAULT (datetime('now')),
    system_prompt    TEXT,
    user_prompt      TEXT,
    tool_name        TEXT,
    tool_args        TEXT,
    tool_result      TEXT,
    tool_duration_ms INTEGER,
    llm_request      TEXT,
    llm_response     TEXT,
    token_usage      TEXT,
    error_message    TEXT,
    error_trace      TEXT,
    duration_ms      INTEGER,
    metadata         TEXT
);

CREATE INDEX idx_task_audit_task_id ON task_audit_log (task_id);
CREATE INDEX idx_task_audit_execution ON task_audit_log (execution_id);
CREATE INDEX idx_task_audit_type ON task_audit_log (event_type);

-- Delivery audit log: tracks every delivery attempt to a user channel
CREATE TABLE delivery_audit_log (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id         TEXT,
    conversation_id TEXT NOT NULL,
    channel_name    TEXT NOT NULL,
    message         TEXT NOT NULL,
    status          TEXT NOT NULL,
    attempts        INTEGER NOT NULL DEFAULT 1,
    error_message   TEXT,
    duration_ms     INTEGER,
    created_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_delivery_audit_task ON delivery_audit_log (task_id);
CREATE INDEX idx_delivery_audit_conv ON delivery_audit_log (conversation_id);
