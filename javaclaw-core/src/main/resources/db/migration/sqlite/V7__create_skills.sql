-- V7: Skills
CREATE TABLE skills (
    id          TEXT    PRIMARY KEY,
    owner_id    TEXT    REFERENCES users(id) ON DELETE CASCADE,
    name        TEXT    NOT NULL,
    description TEXT,
    content     TEXT,
    enabled     INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at  TEXT    NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_skills_owner_id ON skills(owner_id);
CREATE INDEX idx_skills_enabled  ON skills(enabled);
CREATE UNIQUE INDEX uq_skills_user_name
    ON skills(owner_id, name) WHERE owner_id IS NOT NULL;
CREATE UNIQUE INDEX uq_skills_global_name
    ON skills(name) WHERE owner_id IS NULL;
