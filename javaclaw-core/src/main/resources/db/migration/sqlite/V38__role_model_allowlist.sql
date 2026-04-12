-- 15.6.4 Model access per role: restrict which AI models each role can use
-- When no entries exist for a role, all models are allowed (open access).
-- When at least one entry exists, only listed models are permitted.

CREATE TABLE role_model_allowlist (
    id         TEXT PRIMARY KEY,
    role       TEXT NOT NULL,
    model_id   TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (role, model_id)
);

CREATE INDEX idx_role_model_allowlist_role ON role_model_allowlist(role);
