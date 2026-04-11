CREATE TABLE agent_quotas (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         VARCHAR(36)  NOT NULL REFERENCES users(id),
    daily_limit     INT          NOT NULL DEFAULT 100,
    daily_used      INT          NOT NULL DEFAULT 0,
    reset_date      DATE         NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_agent_quotas_user_id ON agent_quotas (user_id);
