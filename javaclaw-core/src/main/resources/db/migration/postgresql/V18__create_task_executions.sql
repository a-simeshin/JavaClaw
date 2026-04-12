-- Task executions: stores prompt, response, tool calls, and error details for each task execution.
-- One-shot tasks have a single execution; recurring tasks accumulate one per firing.

CREATE TABLE task_executions (
    id                VARCHAR(256) PRIMARY KEY,
    task_id           VARCHAR(256) NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    execution_number  INT          NOT NULL DEFAULT 1,
    status            VARCHAR(16)  NOT NULL DEFAULT 'running',
    system_prompt     TEXT,
    user_prompt       TEXT,
    llm_response      TEXT,
    tool_calls        TEXT,
    token_usage       TEXT,
    error_message     TEXT,
    error_trace       TEXT,
    started_at        TIMESTAMP    NOT NULL DEFAULT now(),
    completed_at      TIMESTAMP,
    duration_ms       BIGINT,
    created_at        TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_task_executions_task_id ON task_executions (task_id);
CREATE INDEX idx_task_executions_status ON task_executions (status);
