-- Approval requests: human-in-the-loop approval mechanism for tasks.
-- When a task needs user permission to proceed, an approval request is created
-- and the task blocks until the user responds or the request times out.

CREATE TABLE approval_requests (
    id              TEXT PRIMARY KEY,
    task_id         TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    conversation_id TEXT NOT NULL,
    question        TEXT NOT NULL,
    response        TEXT,
    status          TEXT NOT NULL DEFAULT 'pending',
    timeout_at      TEXT NOT NULL,
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    responded_at    TEXT
);

CREATE INDEX idx_approval_requests_task_id ON approval_requests (task_id);
CREATE INDEX idx_approval_requests_conv_status ON approval_requests (conversation_id, status);
CREATE INDEX idx_approval_requests_status_timeout ON approval_requests (status, timeout_at);
