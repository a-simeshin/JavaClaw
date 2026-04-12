package ai.javaclaw.persistence.id;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.mcp.McpServer;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpServerIdGeneratorCallbackTest {

    private final McpServerIdGeneratorCallback callback = new McpServerIdGeneratorCallback();

    @Test
    void generates_uuid_when_id_is_null() {
        final McpServer input =
                McpServer.newGlobal("server-1", "stdio", "claude-mcp", null, Map.of("X-Api-Key", "secret"), true);

        final McpServer result = callback.onBeforeConvert(input);

        assertThat(result.id()).isNotNull();
        assertThat(result.name()).isEqualTo("server-1");
        assertThat(result.transport()).isEqualTo("stdio");
        assertThat(result.command()).isEqualTo("claude-mcp");
        assertThat(result.headers()).containsEntry("X-Api-Key", "secret");
        assertThat(result.enabled()).isTrue();
    }

    @Test
    void keeps_existing_id() {
        final McpServer base = McpServer.newGlobal("x", "stdio", "cmd", null, null, true);
        final McpServer withId = new McpServer(
                "fixed-id",
                base.ownerId(),
                base.name(),
                base.transport(),
                base.command(),
                base.url(),
                base.headers(),
                base.enabled(),
                base.visibility(),
                base.createdAt(),
                base.updatedAt(),
                base.healthStatus(),
                base.healthDetail(),
                base.lastHealthCheckAt());

        final McpServer result = callback.onBeforeConvert(withId);

        assertThat(result).isSameAs(withId);
        assertThat(result.id()).isEqualTo("fixed-id");
    }
}
