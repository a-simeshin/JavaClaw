-- V41: Relax user_session.user_id foreign key.
--
-- OpaqueSessionTokenService stores the authenticated principal name (username)
-- into the user_id column — not the users.id UUID. The original FK to users(id)
-- therefore could never be satisfied, causing Spring Data JDBC saves to either
-- fail or (due to the Persistable-isNew bug) be silently skipped.
--
-- Drop the FK so session issue() can persist real rows. We keep the user_id
-- column as a plain reference (username) and rely on application-level
-- cascade on user deletion.
ALTER TABLE user_session
    DROP CONSTRAINT IF EXISTS fk_user_session_user;
