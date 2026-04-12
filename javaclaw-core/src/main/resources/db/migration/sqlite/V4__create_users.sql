-- V4: Users table for Phase 4.1 Basic Auth
-- Note: CHECK includes POWER_USER from the start to avoid a SQLite table
-- rebuild in V31 (SQLite CHECK constraints cannot be altered). This is
-- semantically equivalent to Postgres V31 widening the CHECK post-hoc.
CREATE TABLE users (
    id            TEXT    PRIMARY KEY,
    username      TEXT    NOT NULL,
    password_hash TEXT,
    role          TEXT    NOT NULL DEFAULT 'USER'
                      CHECK (role IN ('ADMIN', 'USER', 'POWER_USER')),
    active        INTEGER NOT NULL DEFAULT 1,
    created_at    TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at    TEXT    NOT NULL DEFAULT (datetime('now'))
);
CREATE UNIQUE INDEX uq_users_username ON users(username);
CREATE INDEX idx_users_role           ON users(role);
CREATE INDEX idx_users_active         ON users(active);
