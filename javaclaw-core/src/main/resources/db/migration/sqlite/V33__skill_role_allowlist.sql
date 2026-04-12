-- 15.3.1-15.3.2: Skill visibility + allowlist per role
-- Adds visibility scope to skills and a mapping table linking skills to allowed roles.

-- Add visibility column: PUBLIC (all roles see it), RESTRICTED (only allowlisted roles)
ALTER TABLE skills ADD COLUMN visibility TEXT NOT NULL DEFAULT 'PUBLIC';

-- Mapping table: which roles can see/execute a RESTRICTED skill
CREATE TABLE skill_role_allowlist (
    id         TEXT PRIMARY KEY,
    skill_id   TEXT NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    role       TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (skill_id, role)
);

CREATE INDEX idx_skill_role_allowlist_skill ON skill_role_allowlist(skill_id);
CREATE INDEX idx_skill_role_allowlist_role  ON skill_role_allowlist(role);
