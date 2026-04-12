package ai.javaclaw.persistence.id;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.agent.pipeline.ToolExample;
import org.junit.jupiter.api.Test;

class ToolExampleIdGeneratorCallbackTest {

    private final ToolExampleIdGeneratorCallback callback = new ToolExampleIdGeneratorCallback();

    @Test
    void generates_uuid_when_id_is_null() {
        final ToolExample input =
                ToolExample.create("search", null, 1, "find cats", "let me search", "search(\"cats\")", "1 result");

        final ToolExample result = callback.onBeforeConvert(input);

        assertThat(result.id()).isNotNull();
        assertThat(result.toolName()).isEqualTo("search");
        assertThat(result.exampleOrder()).isEqualTo(1);
        assertThat(result.userMessage()).isEqualTo("find cats");
        assertThat(result.toolCall()).isEqualTo("search(\"cats\")");
        assertThat(result.toolResult()).isEqualTo("1 result");
    }

    @Test
    void keeps_existing_id() {
        final ToolExample input = new ToolExample("fixed-id", "t", null, 0, "u", null, "tc", null);

        final ToolExample result = callback.onBeforeConvert(input);

        assertThat(result).isSameAs(input);
        assertThat(result.id()).isEqualTo("fixed-id");
    }
}
