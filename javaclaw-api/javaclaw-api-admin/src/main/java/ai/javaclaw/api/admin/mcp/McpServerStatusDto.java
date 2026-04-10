package ai.javaclaw.api.admin.mcp;

/** Connection-state snapshot for an MCP server. */
public record McpServerStatusDto(String id, String status, String detail, String checkedAt) {

    /** Convenience constructor without checkedAt (backward-compatible). */
    public McpServerStatusDto(final String id, final String status, final String detail) {
        this(id, status, detail, null);
    }
}
