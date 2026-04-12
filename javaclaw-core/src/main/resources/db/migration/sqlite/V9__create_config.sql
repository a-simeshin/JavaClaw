-- V9: Config
CREATE TABLE config (
    id           TEXT PRIMARY KEY,
    config_key   TEXT NOT NULL,
    scope        TEXT NOT NULL
                     CHECK (scope IN ('global', 'user')),
    owner_id     TEXT REFERENCES users(id) ON DELETE CASCADE,
    config_value TEXT,
    created_at   TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at   TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_config_config_key ON config(config_key);
CREATE INDEX idx_config_owner_id   ON config(owner_id);
CREATE UNIQUE INDEX uq_config_user_scoped
    ON config(config_key, scope, owner_id) WHERE owner_id IS NOT NULL;
CREATE UNIQUE INDEX uq_config_global_scoped
    ON config(config_key, scope) WHERE owner_id IS NULL;
