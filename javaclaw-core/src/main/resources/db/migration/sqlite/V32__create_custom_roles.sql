-- V32: Custom roles support (Phase 13 — 15.2.4)
-- Postgres drops the users_role_check constraint here; SQLite V4 already omitted
-- it (CHECK constraints cannot be dropped via ALTER) so no-op on SQLite side.

-- Custom roles metadata table
CREATE TABLE custom_roles (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL UNIQUE,
    description TEXT,
    built_in    INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_custom_roles_name ON custom_roles(name);

-- Seed built-in roles as reference entries
INSERT INTO custom_roles (name, description, built_in) VALUES
    ('ADMIN', 'Full system administrator with all permissions', 1),
    ('POWER_USER', 'Advanced user with extended permissions', 1),
    ('USER', 'Standard user with basic permissions', 1);
