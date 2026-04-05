-- V9: Config
CREATE TABLE config (
    id           VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    config_key   VARCHAR(255) NOT NULL,
    scope        VARCHAR(16)  NOT NULL
                     CHECK (scope IN ('global', 'user')),
    owner_id     VARCHAR(36)  REFERENCES users(id) ON DELETE CASCADE,
    config_value TEXT,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_config_config_key ON config(config_key);
CREATE INDEX idx_config_owner_id   ON config(owner_id);
CREATE UNIQUE INDEX uq_config_user_scoped
    ON config(config_key, scope, owner_id) WHERE owner_id IS NOT NULL;
CREATE UNIQUE INDEX uq_config_global_scoped
    ON config(config_key, scope) WHERE owner_id IS NULL;
