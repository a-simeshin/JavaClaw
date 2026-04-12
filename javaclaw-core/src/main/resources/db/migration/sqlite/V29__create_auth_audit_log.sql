-- Auth audit log for compliance: tracks login success, login failure, access denied events
CREATE TABLE auth_audit_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type  TEXT    NOT NULL,       -- login_success, login_failure, access_denied
    username    TEXT,                    -- attempted username (may not exist in users table)
    remote_addr TEXT,                    -- client IP address
    request_uri TEXT,                    -- requested endpoint
    detail      TEXT,                    -- additional context (e.g. failure reason, role required)
    created_at  TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_auth_audit_log_created_at ON auth_audit_log(created_at);
CREATE INDEX idx_auth_audit_log_username   ON auth_audit_log(username);
CREATE INDEX idx_auth_audit_log_event_type ON auth_audit_log(event_type);
