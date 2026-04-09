CREATE TABLE chat_audit_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id VARCHAR(256) NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    method          VARCHAR(16)  NOT NULL,
    system_prompt   TEXT,
    history         TEXT,
    user_content    TEXT         NOT NULL,
    tool_names      TEXT,
    response_text   TEXT,
    error_message   TEXT,
    error_trace     TEXT,
    duration_ms     BIGINT
);

CREATE INDEX idx_chat_audit_log_conversation ON chat_audit_log (conversation_id);
CREATE INDEX idx_chat_audit_log_created_at   ON chat_audit_log (created_at);
