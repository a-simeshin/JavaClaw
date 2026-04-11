package ai.javaclaw.agent.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RoleAgentConfig} entity. */
class RoleAgentConfigTest {

    @Test
    void create_setsFieldsCorrectly() {
        RoleAgentConfig config =
                RoleAgentConfig.create("ADMIN", "gpt-4o", true, 50000, "gpt-4o-mini,claude-3-haiku", 200000);
        assertThat(config.id()).isNull();
        assertThat(config.role()).isEqualTo("ADMIN");
        assertThat(config.modelId()).isEqualTo("gpt-4o");
        assertThat(config.thinkingEnabled()).isTrue();
        assertThat(config.thinkingBudget()).isEqualTo(50000);
        assertThat(config.fallbackModels()).isEqualTo("gpt-4o-mini,claude-3-haiku");
        assertThat(config.maxContextTokens()).isEqualTo(200000);
    }

    @Test
    void fallbackModelList_parsesCommaSeparated() {
        RoleAgentConfig config =
                RoleAgentConfig.create("USER", null, false, 10000, "model-a, model-b , model-c", 100000);
        List<String> list = config.fallbackModelList();
        assertThat(list).containsExactly("model-a", "model-b", "model-c");
    }

    @Test
    void fallbackModelList_emptyWhenNull() {
        RoleAgentConfig config = RoleAgentConfig.create("USER", null, false, 10000, null, 100000);
        assertThat(config.fallbackModelList()).isEmpty();
    }

    @Test
    void fallbackModelList_emptyWhenBlank() {
        RoleAgentConfig config = RoleAgentConfig.create("USER", null, false, 10000, "  ", 100000);
        assertThat(config.fallbackModelList()).isEmpty();
    }

    @Test
    void withModelId_returnsNewInstance() {
        RoleAgentConfig original = RoleAgentConfig.create("ADMIN", "gpt-4o", true, 50000, null, 200000);
        RoleAgentConfig updated = original.withModelId("claude-3-opus");
        assertThat(updated.modelId()).isEqualTo("claude-3-opus");
        assertThat(updated.role()).isEqualTo("ADMIN");
        assertThat(updated.thinkingEnabled()).isTrue();
    }

    @Test
    void withThinking_returnsNewInstance() {
        RoleAgentConfig original = RoleAgentConfig.create("ADMIN", "gpt-4o", false, 10000, null, 200000);
        RoleAgentConfig updated = original.withThinking(true, 50000);
        assertThat(updated.thinkingEnabled()).isTrue();
        assertThat(updated.thinkingBudget()).isEqualTo(50000);
        assertThat(updated.modelId()).isEqualTo("gpt-4o");
    }

    @Test
    void withMaxContextTokens_returnsNewInstance() {
        RoleAgentConfig original = RoleAgentConfig.create("USER", null, false, 10000, null, 100000);
        RoleAgentConfig updated = original.withMaxContextTokens(50000);
        assertThat(updated.maxContextTokens()).isEqualTo(50000);
        assertThat(updated.role()).isEqualTo("USER");
    }
}
