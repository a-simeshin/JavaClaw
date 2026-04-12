-- V7: Skills
CREATE TABLE skills (
    id          VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    owner_id    VARCHAR(36)  REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    content     TEXT,
    enabled     BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_skills_owner_id ON skills(owner_id);
CREATE INDEX idx_skills_enabled  ON skills(enabled);
CREATE UNIQUE INDEX uq_skills_user_name
    ON skills(owner_id, name) WHERE owner_id IS NOT NULL;
CREATE UNIQUE INDEX uq_skills_global_name
    ON skills(name) WHERE owner_id IS NULL;
