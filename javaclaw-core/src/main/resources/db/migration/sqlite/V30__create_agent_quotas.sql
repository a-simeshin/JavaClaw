CREATE TABLE agent_quotas (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id         TEXT    NOT NULL REFERENCES users(id),
    daily_limit     INTEGER NOT NULL DEFAULT 100,
    daily_used      INTEGER NOT NULL DEFAULT 0,
    reset_date      TEXT    NOT NULL DEFAULT (date('now')),
    created_at      TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX idx_agent_quotas_user_id ON agent_quotas (user_id);
