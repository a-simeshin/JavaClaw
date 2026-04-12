-- Removes DEFAULT gen_random_uuid() from PK columns.
-- UUID generation moves to Java BeforeConvertCallback (Task #7).
-- This enables portable multi-dialect persistence (SQLite has no gen_random_uuid).
--
-- See specs/persistence-spi-design.md §4.5 for the full list.
-- V10__seed_default_users.sql is intentionally untouched: it calls
-- gen_random_uuid() inside INSERT ... VALUES, not as a column default.

ALTER TABLE tasks                  ALTER COLUMN id DROP DEFAULT;
ALTER TABLE recurring_tasks        ALTER COLUMN id DROP DEFAULT;
ALTER TABLE users                  ALTER COLUMN id DROP DEFAULT;
ALTER TABLE virtual_files          ALTER COLUMN id DROP DEFAULT;
ALTER TABLE skills                 ALTER COLUMN id DROP DEFAULT;
ALTER TABLE mcp_servers            ALTER COLUMN id DROP DEFAULT;
ALTER TABLE config                 ALTER COLUMN id DROP DEFAULT;
ALTER TABLE conversation_summaries ALTER COLUMN id DROP DEFAULT;
ALTER TABLE tool_examples          ALTER COLUMN id DROP DEFAULT;
ALTER TABLE memories               ALTER COLUMN id DROP DEFAULT;
ALTER TABLE skill_role_allowlist   ALTER COLUMN id DROP DEFAULT;
ALTER TABLE role_agent_config      ALTER COLUMN id DROP DEFAULT;
ALTER TABLE role_model_allowlist   ALTER COLUMN id DROP DEFAULT;
