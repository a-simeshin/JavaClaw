-- V10: Seed default users with deterministic literal UUIDs.
-- See specs/persistence-spi-design.md §4.4.
-- password_hash IS NULL until V23 populates the {noop} seed values.
INSERT INTO users (id, username, password_hash, role, active)
VALUES ('00000000-0000-0000-0000-000000000001', 'admin', NULL, 'ADMIN', 1)
ON CONFLICT DO NOTHING;
INSERT INTO users (id, username, password_hash, role, active)
VALUES ('00000000-0000-0000-0000-000000000002', 'user', NULL, 'USER', 1)
ON CONFLICT DO NOTHING;
