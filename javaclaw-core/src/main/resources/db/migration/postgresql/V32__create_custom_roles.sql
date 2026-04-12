-- V32: Custom roles support (Phase 13 — 15.2.4)

-- Remove hardcoded role CHECK constraint to allow custom role names
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;

-- Custom roles metadata table
CREATE TABLE custom_roles (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(32) NOT NULL UNIQUE,
    description VARCHAR(255),
    built_in    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_custom_roles_name ON custom_roles(name);

-- Seed built-in roles as reference entries
INSERT INTO custom_roles (name, description, built_in) VALUES
    ('ADMIN', 'Full system administrator with all permissions', TRUE),
    ('POWER_USER', 'Advanced user with extended permissions', TRUE),
    ('USER', 'Standard user with basic permissions', TRUE);
