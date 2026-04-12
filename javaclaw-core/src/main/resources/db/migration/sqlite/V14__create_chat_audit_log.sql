CREATE TABLE chat_audit_log (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id TEXT    NOT NULL,
    created_at      TEXT    NOT NULL DEFAULT (datetime('now')),
    method          TEXT    NOT NULL,
    system_prompt   TEXT,
    history         TEXT,
    user_content    TEXT    NOT NULL,
    tool_names      TEXT,
    response_text   TEXT,
    error_message   TEXT,
    error_trace     TEXT,
    duration_ms     INTEGER
);

CREATE INDEX idx_chat_audit_log_conversation ON chat_audit_log (conversation_id);
CREATE INDEX idx_chat_audit_log_created_at   ON chat_audit_log (created_at);
