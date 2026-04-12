-- V23: Populate password_hash for seed users (Phase 7.1 — DB-backed auth)
-- Uses {noop} prefix for DelegatingPasswordEncoder compatibility.
-- Production deployments should update passwords via /api/users/{id}/password (bcrypt-encoded).
UPDATE users SET password_hash = '{noop}admin'
    WHERE username = 'admin' AND password_hash IS NULL;
UPDATE users SET password_hash = '{noop}user'
    WHERE username = 'user' AND password_hash IS NULL;
