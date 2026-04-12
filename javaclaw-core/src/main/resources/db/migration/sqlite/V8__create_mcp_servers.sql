-- V8: MCP Servers
CREATE TABLE mcp_servers (
    id         TEXT    PRIMARY KEY,
    owner_id   TEXT    REFERENCES users(id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    transport  TEXT    NOT NULL
                   CHECK (transport IN ('stdio', 'http')),
    command    TEXT,
    url        TEXT,
    headers    TEXT,
    enabled    INTEGER NOT NULL DEFAULT 0,
    created_at TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT    NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_mcp_servers_owner_id ON mcp_servers(owner_id);
CREATE INDEX idx_mcp_servers_enabled  ON mcp_servers(enabled);
CREATE UNIQUE INDEX uq_mcp_servers_user_name
    ON mcp_servers(owner_id, name) WHERE owner_id IS NOT NULL;
CREATE UNIQUE INDEX uq_mcp_servers_global_name
    ON mcp_servers(name) WHERE owner_id IS NULL;
