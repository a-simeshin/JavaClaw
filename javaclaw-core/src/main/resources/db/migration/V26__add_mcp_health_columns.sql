-- V26: Add health check columns to mcp_servers for real-time health monitoring
ALTER TABLE mcp_servers
    ADD COLUMN health_status  VARCHAR(20) NOT NULL DEFAULT 'unknown',
    ADD COLUMN health_detail  TEXT,
    ADD COLUMN last_health_check_at TIMESTAMPTZ;
