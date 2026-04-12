package ai.javaclaw.persistence.id;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.mcp.McpRoleAllowlist;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class McpRoleAllowlistIdGeneratorCallbackTest {

    private final McpRoleAllowlistIdGeneratorCallback callback = new McpRoleAllowlistIdGeneratorCallback();

    @Test
    void generates_uuid_when_id_is_null() {
        final McpRoleAllowlist input = McpRoleAllowlist.create("srv-1", "ADMIN");

        final McpRoleAllowlist result = callback.onBeforeConvert(input);

        assertThat(result.id()).isNotNull();
        assertThat(result.serverId()).isEqualTo("srv-1");
        assertThat(result.role()).isEqualTo("ADMIN");
        assertThat(result.createdAt()).isEqualTo(input.createdAt());
    }

    @Test
    void keeps_existing_id() {
        final McpRoleAllowlist input = new McpRoleAllowlist("fixed-id", "srv-2", "USER", Instant.EPOCH);

        final McpRoleAllowlist result = callback.onBeforeConvert(input);

        assertThat(result).isSameAs(input);
        assertThat(result.id()).isEqualTo("fixed-id");
    }
}
