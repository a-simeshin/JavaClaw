-- 15.6.4 Model access per role: restrict which AI models each role can use
-- When no entries exist for a role, all models are allowed (open access).
-- When at least one entry exists, only listed models are permitted.

CREATE TABLE role_model_allowlist (
    id          VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    role        VARCHAR(64)  NOT NULL,
    model_id    VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    UNIQUE (role, model_id)
);

CREATE INDEX idx_role_model_allowlist_role ON role_model_allowlist(role);
