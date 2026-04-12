-- Auth audit log for compliance: tracks login success, login failure, access denied events
CREATE TABLE auth_audit_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type  VARCHAR(32)  NOT NULL,       -- login_success, login_failure, access_denied
    username    VARCHAR(255),                 -- attempted username (may not exist in users table)
    remote_addr VARCHAR(64),                 -- client IP address
    request_uri VARCHAR(1024),               -- requested endpoint
    detail      TEXT,                         -- additional context (e.g. failure reason, role required)
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_auth_audit_log_created_at ON auth_audit_log(created_at);
CREATE INDEX idx_auth_audit_log_username   ON auth_audit_log(username);
CREATE INDEX idx_auth_audit_log_event_type ON auth_audit_log(event_type);
