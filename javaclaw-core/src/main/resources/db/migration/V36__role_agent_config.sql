-- 15.6.1 Agent assignment per role — per-role agent configuration (model, thinking, fallback)
CREATE TABLE role_agent_config (
    id          VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    role        VARCHAR(64) NOT NULL UNIQUE,
    model_id    VARCHAR(255),
    thinking_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    thinking_budget     INTEGER NOT NULL DEFAULT 10000,
    fallback_models     TEXT,
    max_context_tokens  INTEGER NOT NULL DEFAULT 200000,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);

CREATE INDEX idx_role_agent_config_role ON role_agent_config(role);

-- Seed: ADMIN gets full access (no model restriction = uses system default)
INSERT INTO role_agent_config (role, thinking_enabled, thinking_budget, max_context_tokens)
VALUES ('ADMIN', TRUE, 50000, 200000);

-- USER: conservative defaults
INSERT INTO role_agent_config (role, thinking_enabled, thinking_budget, max_context_tokens)
VALUES ('USER', FALSE, 10000, 100000);
