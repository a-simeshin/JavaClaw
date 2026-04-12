-- auth_audit_log.event_type is a free VARCHAR(32) with no CHECK constraint (see V29).
-- New event types added by cookie-session auth: session_created, session_revoked,
-- logout, logout_all, second_factor_challenge, second_factor_success, second_factor_failure.
-- No DDL change required — values are unconstrained.
-- Extending column length to 64 to accommodate longer event type names.
ALTER TABLE auth_audit_log ALTER COLUMN event_type TYPE VARCHAR(64);
