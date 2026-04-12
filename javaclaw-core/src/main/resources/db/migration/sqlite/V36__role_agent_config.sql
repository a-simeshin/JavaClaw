-- 15.6.1 Agent assignment per role — per-role agent configuration (model, thinking, fallback)
CREATE TABLE role_agent_config (
    id                 TEXT PRIMARY KEY,
    role               TEXT NOT NULL UNIQUE,
    model_id           TEXT,
    thinking_enabled   INTEGER NOT NULL DEFAULT 0,
    thinking_budget    INTEGER NOT NULL DEFAULT 10000,
    fallback_models    TEXT,
    max_context_tokens INTEGER NOT NULL DEFAULT 200000,
    created_at         TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at         TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_role_agent_config_role ON role_agent_config(role);

-- Seed: ADMIN gets full access (no model restriction = uses system default).
-- SQLite requires an explicit id (Java-side defaults do not fire inside Flyway SQL).
INSERT INTO role_agent_config (id, role, thinking_enabled, thinking_budget, max_context_tokens)
VALUES ('00000000-0000-0000-0000-000000000101', 'ADMIN', 1, 50000, 200000);

-- USER: conservative defaults
INSERT INTO role_agent_config (id, role, thinking_enabled, thinking_budget, max_context_tokens)
VALUES ('00000000-0000-0000-0000-000000000102', 'USER', 0, 10000, 100000);
