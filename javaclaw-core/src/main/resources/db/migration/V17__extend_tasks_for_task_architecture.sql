-- V17: Extend tasks table for full task architecture (Phase 1)
-- Adds: parent-child hierarchy, notify policy, runtime type, timeout,
--        carry-over context, user ownership, lifecycle timestamps, optimistic locking

ALTER TABLE tasks ADD COLUMN parent_task_id VARCHAR(36) REFERENCES tasks(id);
ALTER TABLE tasks ADD COLUMN notify_policy VARCHAR(32) DEFAULT 'done_only';
ALTER TABLE tasks ADD COLUMN runtime_type VARCHAR(16) DEFAULT 'async';
ALTER TABLE tasks ADD COLUMN timeout_seconds INT;
ALTER TABLE tasks ADD COLUMN carry_over_context BOOLEAN DEFAULT FALSE;
ALTER TABLE tasks ADD COLUMN user_id VARCHAR(36);
ALTER TABLE tasks ADD COLUMN failed_at TIMESTAMP;
ALTER TABLE tasks ADD COLUMN cancelled_at TIMESTAMP;
ALTER TABLE tasks ADD COLUMN version INT NOT NULL DEFAULT 0;

CREATE INDEX idx_tasks_parent_task_id ON tasks(parent_task_id);
CREATE INDEX idx_tasks_user_id ON tasks(user_id);
CREATE INDEX idx_tasks_runtime_type ON tasks(runtime_type);
