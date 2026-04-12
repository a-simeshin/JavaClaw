-- V31: Granular permissions + role hierarchy (Phase 13 — 15.2.2 + 15.2.3)
-- Note: the Postgres version drops and re-adds a CHECK constraint on users.role.
-- SQLite does not support ALTER TABLE ... DROP CONSTRAINT and CHECK constraints
-- are baked into the CREATE TABLE. The role column ends up unconstrained in V32
-- anyway, so on SQLite we simply skip the CHECK manipulation here.

-- Role permissions mapping table
CREATE TABLE role_permissions (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    role       TEXT NOT NULL,
    permission TEXT NOT NULL,
    CONSTRAINT uq_role_permission UNIQUE (role, permission)
);
CREATE INDEX idx_role_permissions_role ON role_permissions(role);

-- Seed: USER permissions (basic operations)
INSERT INTO role_permissions (role, permission) VALUES
    ('USER', 'CHAT_SEND'),
    ('USER', 'CHAT_READ'),
    ('USER', 'CONVERSATION_CREATE'),
    ('USER', 'CONVERSATION_LIST'),
    ('USER', 'CONVERSATION_DELETE'),
    ('USER', 'FILE_READ'),
    ('USER', 'FILE_WRITE'),
    ('USER', 'FILE_DELETE'),
    ('USER', 'FILE_UPLOAD'),
    ('USER', 'FILE_DOWNLOAD'),
    ('USER', 'SKILL_LIST'),
    ('USER', 'SKILL_EXECUTE'),
    ('USER', 'MCP_LIST'),
    ('USER', 'MCP_CONNECT'),
    ('USER', 'TASK_CREATE'),
    ('USER', 'TASK_LIST'),
    ('USER', 'AGENT_CHAT');

-- Seed: POWER_USER inherits USER + extras
INSERT INTO role_permissions (role, permission) VALUES
    ('POWER_USER', 'CHAT_SEND'),
    ('POWER_USER', 'CHAT_READ'),
    ('POWER_USER', 'CHAT_DELETE'),
    ('POWER_USER', 'CONVERSATION_CREATE'),
    ('POWER_USER', 'CONVERSATION_LIST'),
    ('POWER_USER', 'CONVERSATION_DELETE'),
    ('POWER_USER', 'CONVERSATION_SHARE'),
    ('POWER_USER', 'FILE_READ'),
    ('POWER_USER', 'FILE_WRITE'),
    ('POWER_USER', 'FILE_DELETE'),
    ('POWER_USER', 'FILE_UPLOAD'),
    ('POWER_USER', 'FILE_DOWNLOAD'),
    ('POWER_USER', 'SKILL_LIST'),
    ('POWER_USER', 'SKILL_CREATE'),
    ('POWER_USER', 'SKILL_UPDATE'),
    ('POWER_USER', 'SKILL_EXECUTE'),
    ('POWER_USER', 'MCP_LIST'),
    ('POWER_USER', 'MCP_CREATE'),
    ('POWER_USER', 'MCP_UPDATE'),
    ('POWER_USER', 'MCP_CONNECT'),
    ('POWER_USER', 'TASK_CREATE'),
    ('POWER_USER', 'TASK_LIST'),
    ('POWER_USER', 'TASK_CANCEL'),
    ('POWER_USER', 'TASK_APPROVE'),
    ('POWER_USER', 'AGENT_CHAT'),
    ('POWER_USER', 'AGENT_CONFIGURE');

-- Seed: ADMIN gets ALL permissions
INSERT INTO role_permissions (role, permission) VALUES
    ('ADMIN', 'CHAT_SEND'),
    ('ADMIN', 'CHAT_READ'),
    ('ADMIN', 'CHAT_DELETE'),
    ('ADMIN', 'CONVERSATION_CREATE'),
    ('ADMIN', 'CONVERSATION_LIST'),
    ('ADMIN', 'CONVERSATION_DELETE'),
    ('ADMIN', 'CONVERSATION_SHARE'),
    ('ADMIN', 'CONVERSATION_ACCESS_ALL'),
    ('ADMIN', 'FILE_READ'),
    ('ADMIN', 'FILE_WRITE'),
    ('ADMIN', 'FILE_DELETE'),
    ('ADMIN', 'FILE_UPLOAD'),
    ('ADMIN', 'FILE_DOWNLOAD'),
    ('ADMIN', 'SKILL_LIST'),
    ('ADMIN', 'SKILL_CREATE'),
    ('ADMIN', 'SKILL_UPDATE'),
    ('ADMIN', 'SKILL_DELETE'),
    ('ADMIN', 'SKILL_EXECUTE'),
    ('ADMIN', 'MCP_LIST'),
    ('ADMIN', 'MCP_CREATE'),
    ('ADMIN', 'MCP_UPDATE'),
    ('ADMIN', 'MCP_DELETE'),
    ('ADMIN', 'MCP_CONNECT'),
    ('ADMIN', 'USER_LIST'),
    ('ADMIN', 'USER_CREATE'),
    ('ADMIN', 'USER_UPDATE'),
    ('ADMIN', 'USER_DELETE'),
    ('ADMIN', 'TASK_CREATE'),
    ('ADMIN', 'TASK_LIST'),
    ('ADMIN', 'TASK_CANCEL'),
    ('ADMIN', 'TASK_APPROVE'),
    ('ADMIN', 'AUDIT_READ'),
    ('ADMIN', 'AGENT_CHAT'),
    ('ADMIN', 'AGENT_CONFIGURE'),
    ('ADMIN', 'SYSTEM_ADMIN');
