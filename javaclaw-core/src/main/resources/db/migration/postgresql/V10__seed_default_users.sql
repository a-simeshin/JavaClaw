-- V10: Seed default users (password_hash IS NULL until Phase 4.1)
INSERT INTO users (id, username, password_hash, role, active)
VALUES
    (gen_random_uuid()::varchar, 'admin', NULL, 'ADMIN', true),
    (gen_random_uuid()::varchar, 'user',  NULL, 'USER',  true)
ON CONFLICT DO NOTHING;
