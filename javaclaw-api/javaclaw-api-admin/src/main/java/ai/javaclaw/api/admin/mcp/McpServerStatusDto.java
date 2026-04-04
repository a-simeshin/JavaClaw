package ai.javaclaw.api.admin.mcp;

/** Connection-state snapshot for an MCP server. */
public record McpServerStatusDto(String id, String status, String detail) {}
