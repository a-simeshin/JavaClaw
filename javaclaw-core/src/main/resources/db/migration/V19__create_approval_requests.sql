-- Approval requests: human-in-the-loop approval mechanism for tasks.
-- When a task needs user permission to proceed, an approval request is created
-- and the task blocks until the user responds or the request times out.

CREATE TABLE approval_requests (
    id              VARCHAR(256) PRIMARY KEY,
    task_id         VARCHAR(256) NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    conversation_id VARCHAR(256) NOT NULL,
    question        TEXT         NOT NULL,
    response        TEXT,
    status          VARCHAR(16)  NOT NULL DEFAULT 'pending',
    timeout_at      TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    responded_at    TIMESTAMP
);

CREATE INDEX idx_approval_requests_task_id ON approval_requests (task_id);
CREATE INDEX idx_approval_requests_conv_status ON approval_requests (conversation_id, status);
CREATE INDEX idx_approval_requests_status_timeout ON approval_requests (status, timeout_at);
