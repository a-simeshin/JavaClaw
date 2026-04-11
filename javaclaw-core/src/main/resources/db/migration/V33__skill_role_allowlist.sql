-- 15.3.1-15.3.2: Skill visibility + allowlist per role
-- Adds visibility scope to skills and a mapping table linking skills to allowed roles.

-- Add visibility column: PUBLIC (all roles see it), RESTRICTED (only allowlisted roles)
ALTER TABLE skills ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC';

-- Mapping table: which roles can see/execute a RESTRICTED skill
CREATE TABLE skill_role_allowlist (
    id         VARCHAR(36)  NOT NULL DEFAULT gen_random_uuid()::text,
    skill_id   VARCHAR(36)  NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    role       VARCHAR(32)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    UNIQUE (skill_id, role)
);

CREATE INDEX idx_skill_role_allowlist_skill ON skill_role_allowlist(skill_id);
CREATE INDEX idx_skill_role_allowlist_role  ON skill_role_allowlist(role);
