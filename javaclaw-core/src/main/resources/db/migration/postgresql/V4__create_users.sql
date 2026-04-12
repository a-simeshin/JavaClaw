-- V4: Users table for Phase 4.1 Basic Auth
CREATE TABLE users (
    id            VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255),
    role          VARCHAR(32)  NOT NULL DEFAULT 'USER'
                      CHECK (role IN ('ADMIN', 'USER')),
    active        BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_users_username ON users(username);
CREATE INDEX idx_users_role           ON users(role);
CREATE INDEX idx_users_active         ON users(active);
