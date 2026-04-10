package ai.javaclaw.mcp;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("mcp_servers")
public record McpServer(
        @Id String id,
        @Column("owner_id") String ownerId,
        @Column("name") String name,
        @Column("transport") String transport,
        @Column("command") String command,
        @Column("url") String url,
        @Column("headers") Map<String, String> headers,
        @Column("enabled") boolean enabled,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt,
        @Column("health_status") String healthStatus,
        @Column("health_detail") String healthDetail,
        @Column("last_health_check_at") Instant lastHealthCheckAt) {

    public static McpServer newGlobal(
            final String name,
            final String transport,
            final String command,
            final String url,
            final Map<String, String> headers,
            final boolean enabled) {
        return new McpServer(
                null,
                null,
                name,
                transport,
                command,
                url,
                headers != null ? headers : Map.of(),
                enabled,
                Instant.now(),
                Instant.now(),
                "unknown",
                null,
                null);
    }

    public McpServer withUpdate(
            final String newName,
            final String newTransport,
            final String newCommand,
            final String newUrl,
            final Map<String, String> newHeaders,
            final boolean newEnabled) {
        return new McpServer(
                this.id,
                this.ownerId,
                newName,
                newTransport,
                newCommand,
                newUrl,
                newHeaders != null ? newHeaders : Map.of(),
                newEnabled,
                this.createdAt,
                Instant.now(),
                this.healthStatus,
                this.healthDetail,
                this.lastHealthCheckAt);
    }

    public McpServer withHealthCheck(final String status, final String detail) {
        return new McpServer(
                this.id,
                this.ownerId,
                this.name,
                this.transport,
                this.command,
                this.url,
                this.headers,
                this.enabled,
                this.createdAt,
                this.updatedAt,
                status,
                detail,
                Instant.now());
    }
}
