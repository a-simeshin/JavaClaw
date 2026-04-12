package ai.javaclaw.persistence.id;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.agent.config.RoleModelAllowlist;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RoleModelAllowlistIdGeneratorCallbackTest {

    private final RoleModelAllowlistIdGeneratorCallback callback = new RoleModelAllowlistIdGeneratorCallback();

    @Test
    void generates_uuid_when_id_is_null() {
        final RoleModelAllowlist input = RoleModelAllowlist.create("ADMIN", "claude-opus");

        final RoleModelAllowlist result = callback.onBeforeConvert(input);

        assertThat(result.id()).isNotNull();
        assertThat(result.role()).isEqualTo("ADMIN");
        assertThat(result.modelId()).isEqualTo("claude-opus");
        assertThat(result.createdAt()).isEqualTo(input.createdAt());
    }

    @Test
    void keeps_existing_id() {
        final RoleModelAllowlist input = new RoleModelAllowlist("fixed-id", "USER", "gpt-4", Instant.EPOCH);

        final RoleModelAllowlist result = callback.onBeforeConvert(input);

        assertThat(result).isSameAs(input);
        assertThat(result.id()).isEqualTo("fixed-id");
    }
}
