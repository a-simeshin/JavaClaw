-- 15.4.1-15.4.3: MCP server visibility + role allowlist + personal servers

-- Add visibility column to mcp_servers (PUBLIC = visible to all, RESTRICTED = allowlisted roles only)
ALTER TABLE mcp_servers ADD COLUMN visibility TEXT NOT NULL DEFAULT 'PUBLIC';

-- Role allowlist table for RESTRICTED MCP servers
CREATE TABLE mcp_role_allowlist (
    id         TEXT PRIMARY KEY,
    server_id  TEXT NOT NULL REFERENCES mcp_servers(id) ON DELETE CASCADE,
    role       TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (server_id, role)
);

CREATE INDEX idx_mcp_role_allowlist_server ON mcp_role_allowlist(server_id);
CREATE INDEX idx_mcp_role_allowlist_role   ON mcp_role_allowlist(role);
