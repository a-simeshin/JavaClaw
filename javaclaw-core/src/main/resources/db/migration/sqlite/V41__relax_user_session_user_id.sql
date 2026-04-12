-- V41: Relax user_session.user_id foreign key by rebuilding the table without the FK.
--
-- OpaqueSessionTokenService stores the authenticated principal name (username)
-- in the user_id column, not the users.id UUID. The original FK could never be
-- satisfied. SQLite does not support DROP CONSTRAINT, so we rebuild the table.

CREATE TABLE user_session_new (
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
    CONSTRAINT pk_user_session_new PRIMARY KEY (id),
    CONSTRAINT uq_user_session_new_token_hash UNIQUE (token_hash)
);

INSERT INTO user_session_new (id, token_hash, user_id, created_at, expires_at,
                              last_used_at, remote_addr, user_agent,
                              revoked_at, revocation_reason)
SELECT id, token_hash, user_id, created_at, expires_at,
       last_used_at, remote_addr, user_agent,
       revoked_at, revocation_reason
FROM user_session;

DROP TABLE user_session;
ALTER TABLE user_session_new RENAME TO user_session;

CREATE INDEX idx_user_session_user_id ON user_session (user_id);
CREATE INDEX idx_user_session_expires_at ON user_session (expires_at);
