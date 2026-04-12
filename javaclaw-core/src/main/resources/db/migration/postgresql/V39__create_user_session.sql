-- User session table for opaque cookie-based authentication
CREATE TABLE user_session (
    id                VARCHAR(36)  NOT NULL,
    token_hash        VARCHAR(64)  NOT NULL,
    user_id           VARCHAR(36)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMPTZ  NOT NULL,
    last_used_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    remote_addr       VARCHAR(64),
    user_agent        VARCHAR(512),
    revoked_at        TIMESTAMPTZ,
    revocation_reason VARCHAR(64),
    CONSTRAINT pk_user_session PRIMARY KEY (id),
    CONSTRAINT uq_user_session_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_user_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_session_user_id ON user_session (user_id);
CREATE INDEX idx_user_session_expires_at ON user_session (expires_at);
