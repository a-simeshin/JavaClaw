package ai.javaclaw.persistence.id;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.agent.config.RoleAgentConfig;
import org.junit.jupiter.api.Test;

class RoleAgentConfigIdGeneratorCallbackTest {

    private final RoleAgentConfigIdGeneratorCallback callback = new RoleAgentConfigIdGeneratorCallback();

    @Test
    void generates_uuid_when_id_is_null() {
        final RoleAgentConfig input =
                RoleAgentConfig.create("ADMIN", "claude-opus", true, 2048, "gpt-4,claude", 200_000);

        final RoleAgentConfig result = callback.onBeforeConvert(input);

        assertThat(result.id()).isNotNull();
        assertThat(result.role()).isEqualTo("ADMIN");
        assertThat(result.modelId()).isEqualTo("claude-opus");
        assertThat(result.thinkingEnabled()).isTrue();
        assertThat(result.thinkingBudget()).isEqualTo(2048);
        assertThat(result.fallbackModels()).isEqualTo("gpt-4,claude");
        assertThat(result.maxContextTokens()).isEqualTo(200_000);
    }

    @Test
    void keeps_existing_id() {
        final RoleAgentConfig input = new RoleAgentConfig("fixed-id", "USER", null, false, 0, null, 100_000);

        final RoleAgentConfig result = callback.onBeforeConvert(input);

        assertThat(result).isSameAs(input);
        assertThat(result.id()).isEqualTo("fixed-id");
    }
}
