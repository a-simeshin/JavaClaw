-- V26: Add health check columns to mcp_servers for real-time health monitoring
ALTER TABLE mcp_servers ADD COLUMN health_status TEXT NOT NULL DEFAULT 'unknown';
ALTER TABLE mcp_servers ADD COLUMN health_detail TEXT;
ALTER TABLE mcp_servers ADD COLUMN last_health_check_at TEXT;
