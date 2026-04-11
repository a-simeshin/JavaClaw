package ai.javaclaw.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class ToolDenyFilterTest {

    @Test
    void checkDenied_globalPattern_blocksDangerousCommand() {
        final var props = new ToolDenyProperties(true, List.of("rm\\s+-rf\\s+/"), Map.of());
        final var filter = new ToolDenyFilter(props);

        final String result = filter.checkDenied("anyTool", "{\"command\": \"rm -rf /\"}");

        assertThat(result).isNotNull().contains("denied").contains("rm\\s+-rf\\s+/");
    }

    @Test
    void checkDenied_globalPattern_allowsSafeCommand() {
        final var props = new ToolDenyProperties(true, List.of("rm\\s+-rf\\s+/"), Map.of());
        final var filter = new ToolDenyFilter(props);

        final String result = filter.checkDenied("anyTool", "{\"path\": \"readme.md\"}");

        assertThat(result).isNull();
    }

    @Test
    void checkDenied_perToolPattern_blocksMatchingTool() {
        final var props = new ToolDenyProperties(true, List.of(), Map.of("writeFile", List.of("\\.sh[\"\\s]")));
        final var filter = new ToolDenyFilter(props);

        final String result = filter.checkDenied("writeFile", "{\"path\": \"script.sh\"}");

        assertThat(result).isNotNull().contains("denied").contains("writeFile");
    }

    @Test
    void checkDenied_perToolPattern_allowsOtherTool() {
        final var props = new ToolDenyProperties(true, List.of(), Map.of("writeFile", List.of("\\.sh[\"\\s]")));
        final var filter = new ToolDenyFilter(props);

        final String result = filter.checkDenied("readFile", "{\"path\": \"script.sh\"}");

        assertThat(result).isNull();
    }

    @Test
    void checkDenied_caseInsensitive() {
        final var props = new ToolDenyProperties(true, List.of("DROP\\s+TABLE"), Map.of());
        final var filter = new ToolDenyFilter(props);

        final String result = filter.checkDenied("anyTool", "drop table users");

        assertThat(result).isNotNull().contains("denied");
    }

    @Test
    void checkDenied_nullArguments_allowed() {
        final var props = new ToolDenyProperties(true, List.of("rm\\s+-rf"), Map.of());
        final var filter = new ToolDenyFilter(props);

        assertThat(filter.checkDenied("anyTool", null)).isNull();
        assertThat(filter.checkDenied("anyTool", "")).isNull();
        assertThat(filter.checkDenied("anyTool", "  ")).isNull();
    }

    @Test
    void checkDenied_invalidRegex_skipped() {
        final var props = new ToolDenyProperties(true, List.of("[invalid"), Map.of());
        final var filter = new ToolDenyFilter(props);

        // Invalid pattern is skipped — nothing is blocked
        final String result = filter.checkDenied("anyTool", "[invalid");

        assertThat(result).isNull();
    }

    @Test
    void wrap_appliesDenyFilterToMatchingCallbacks() {
        final var props = new ToolDenyProperties(true, List.of("DANGER"), Map.of());
        final var filter = new ToolDenyFilter(props);

        final ToolCallback callback = mockCallback("testTool");
        when(callback.call("{\"arg\": \"DANGER\"}")).thenReturn("should not be called");

        final List<ToolCallback> wrapped = filter.wrap(List.of(callback));

        assertThat(wrapped).hasSize(1);
        final String result = wrapped.get(0).call("{\"arg\": \"DANGER\"}");
        assertThat(result).contains("denied");
        verify(callback, never()).call("{\"arg\": \"DANGER\"}");
    }

    @Test
    void wrap_delegatesSafeCallsToOriginal() {
        final var props = new ToolDenyProperties(true, List.of("DANGER"), Map.of());
        final var filter = new ToolDenyFilter(props);

        final ToolCallback callback = mockCallback("testTool");
        when(callback.call("{\"arg\": \"safe\"}")).thenReturn("ok");

        final List<ToolCallback> wrapped = filter.wrap(List.of(callback));

        assertThat(wrapped.get(0).call("{\"arg\": \"safe\"}")).isEqualTo("ok");
        verify(callback).call("{\"arg\": \"safe\"}");
    }

    @Test
    void wrap_skipsWrappingWhenNoPatterns() {
        final var props = new ToolDenyProperties(true, List.of(), Map.of());
        final var filter = new ToolDenyFilter(props);

        final ToolCallback callback = mockCallback("testTool");

        final List<ToolCallback> wrapped = filter.wrap(List.of(callback));

        // No patterns — returned as-is (not wrapped)
        assertThat(wrapped.get(0)).isSameAs(callback);
    }

    @Test
    void constructorRejectsNull() {
        assertThatThrownBy(() -> new ToolDenyFilter(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multipleGlobalAndPerToolPatterns() {
        final var props = new ToolDenyProperties(
                true, List.of("DROP\\s+TABLE", "TRUNCATE"), Map.of("deleteFile", List.of("AGENT\\.md", "SOUL\\.md")));
        final var filter = new ToolDenyFilter(props);

        assertThat(filter.checkDenied("deleteFile", "{\"path\": \"AGENT.md\"}"))
                .isNotNull()
                .contains("deleteFile");
        assertThat(filter.checkDenied("deleteFile", "TRUNCATE something"))
                .isNotNull()
                .contains("TRUNCATE");
        assertThat(filter.checkDenied("deleteFile", "{\"path\": \"readme.md\"}"))
                .isNull();
    }

    private static ToolCallback mockCallback(final String name) {
        final ToolCallback callback = mock(ToolCallback.class);
        final ToolDefinition toolDef = mock(ToolDefinition.class);
        when(toolDef.name()).thenReturn(name);
        when(callback.getToolDefinition()).thenReturn(toolDef);
        return callback;
    }
}
