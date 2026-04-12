package ai.javaclaw.persistence.id;

import ai.javaclaw.mcp.McpServer;
import java.util.UUID;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Генератор идентификаторов для {@link McpServer}.
 *
 * <p>Проставляет случайный UUID для новой записи MCP-сервера, если {@code id} не задан.
 * Делает сохранение через Spring Data JDBC независимым от {@code DEFAULT gen_random_uuid()}
 * PostgreSQL.
 */
@Component
public class McpServerIdGeneratorCallback implements BeforeConvertCallback<McpServer> {

    @Override
    public McpServer onBeforeConvert(final McpServer server) {
        if (server.id() == null) {
            return new McpServer(
                    UUID.randomUUID().toString(),
                    server.ownerId(),
                    server.name(),
                    server.transport(),
                    server.command(),
                    server.url(),
                    server.headers(),
                    server.enabled(),
                    server.visibility(),
                    server.createdAt(),
                    server.updatedAt(),
                    server.healthStatus(),
                    server.healthDetail(),
                    server.lastHealthCheckAt());
        }
        return server;
    }
}
