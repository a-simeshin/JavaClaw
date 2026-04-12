-- Skill usage audit log: tracks who called which skill management operation, when, with what result
CREATE TABLE skill_usage_audit (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    skill_id    TEXT,                    -- skill affected (may be null for failed lookups)
    skill_name  TEXT,                    -- skill name at time of action (denormalized for history)
    event_type  TEXT NOT NULL,           -- created, updated, deleted, enabled, disabled, visibility_changed, allowlist_changed
    username    TEXT NOT NULL,           -- who performed the action
    detail      TEXT,                    -- additional context (e.g. old/new visibility, roles changed)
    created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_skill_usage_audit_created_at ON skill_usage_audit(created_at);
CREATE INDEX idx_skill_usage_audit_skill_id   ON skill_usage_audit(skill_id);
CREATE INDEX idx_skill_usage_audit_username   ON skill_usage_audit(username);
CREATE INDEX idx_skill_usage_audit_event_type ON skill_usage_audit(event_type);
