-- V40: Postgres widens auth_audit_log.event_type from VARCHAR(32) to VARCHAR(64)
-- to accommodate longer event type names (session_created, second_factor_challenge, ...).
-- SQLite ignores column length entirely (TEXT has no declared width), so this is a no-op.
SELECT 1;
