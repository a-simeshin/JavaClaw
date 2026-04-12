-- User session table for opaque cookie-based authentication
CREATE TABLE user_session (
    id                TEXT NOT NULL,
    token_hash        TEXT NOT NULL,
    user_id           TEXT NOT NULL,
    created_at        TEXT NOT NULL DEFAULT (datetime('now')),
    expires_at        TEXT NOT NULL,
    last_used_at      TEXT NOT NULL DEFAULT (datetime('now')),
    remote_addr       TEXT,
    user_agent        TEXT,
    revoked_at        TEXT,
    revocation_reason TEXT,
    CONSTRAINT pk_user_session PRIMARY KEY (id),
    CONSTRAINT uq_user_session_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_user_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_session_user_id ON user_session (user_id);
CREATE INDEX idx_user_session_expires_at ON user_session (expires_at);
