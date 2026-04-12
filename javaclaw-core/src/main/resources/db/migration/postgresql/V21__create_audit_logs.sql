-- Task audit log: full execution history for each task lifecycle event
CREATE TABLE task_audit_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id         VARCHAR(256) NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    execution_id    VARCHAR(256),
    event_type      VARCHAR(32)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    system_prompt   TEXT,
    user_prompt     TEXT,
    tool_name       VARCHAR(128),
    tool_args       TEXT,
    tool_result     TEXT,
    tool_duration_ms BIGINT,
    llm_request     TEXT,
    llm_response    TEXT,
    token_usage     TEXT,
    error_message   TEXT,
    error_trace     TEXT,
    duration_ms     BIGINT,
    metadata        TEXT
);

CREATE INDEX idx_task_audit_task_id ON task_audit_log (task_id);
CREATE INDEX idx_task_audit_execution ON task_audit_log (execution_id);
CREATE INDEX idx_task_audit_type ON task_audit_log (event_type);

-- Delivery audit log: tracks every delivery attempt to a user channel
CREATE TABLE delivery_audit_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id         VARCHAR(256),
    conversation_id VARCHAR(256) NOT NULL,
    channel_name    VARCHAR(64)  NOT NULL,
    message         TEXT         NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 1,
    error_message   TEXT,
    duration_ms     BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_delivery_audit_task ON delivery_audit_log (task_id);
CREATE INDEX idx_delivery_audit_conv ON delivery_audit_log (conversation_id);
