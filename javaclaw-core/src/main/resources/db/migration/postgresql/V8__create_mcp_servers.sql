-- V8: MCP Servers
CREATE TABLE mcp_servers (
    id         VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    owner_id   VARCHAR(36)  REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(120) NOT NULL,
    transport  VARCHAR(10)  NOT NULL
                   CHECK (transport IN ('stdio', 'http')),
    command    TEXT,
    url        TEXT,
    headers    JSONB,
    enabled    BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_mcp_servers_owner_id ON mcp_servers(owner_id);
CREATE INDEX idx_mcp_servers_enabled  ON mcp_servers(enabled);
CREATE UNIQUE INDEX uq_mcp_servers_user_name
    ON mcp_servers(owner_id, name) WHERE owner_id IS NOT NULL;
CREATE UNIQUE INDEX uq_mcp_servers_global_name
    ON mcp_servers(name) WHERE owner_id IS NULL;
