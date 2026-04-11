package ai.javaclaw.mcp;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Maps an MCP server to a role that is allowed to see/use it when visibility is RESTRICTED. */
@Table("mcp_role_allowlist")
public record McpRoleAllowlist(
        @Id String id,
        @Column("server_id") String serverId,
        @Column("role") String role,
        @Column("created_at") Instant createdAt) {

    public static McpRoleAllowlist create(final String serverId, final String role) {
        return new McpRoleAllowlist(null, serverId, role, Instant.now());
    }
}
