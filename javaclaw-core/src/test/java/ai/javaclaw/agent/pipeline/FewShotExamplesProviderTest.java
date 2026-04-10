package ai.javaclaw.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link FewShotExamplesProvider}.
 */
@ExtendWith(MockitoExtension.class)
class FewShotExamplesProviderTest {

    @Mock
    private ToolExampleRepository toolExampleRepository;

    private FewShotExamplesProvider provider;

    @BeforeEach
    void setUp() {
        provider = new FewShotExamplesProvider(toolExampleRepository);
    }

    @Test
    @DisplayName("loadExamples(null): returns empty string when no global examples exist")
    void loadExamples_noExamples_returnsEmpty() {
        when(toolExampleRepository.findAllGlobal()).thenReturn(List.of());
        assertThat(provider.loadExamples(null)).isEmpty();
    }

    @Test
    @DisplayName("loadExamples(null): formats global examples correctly")
    void loadExamples_globalExamples_formattedCorrectly() {
        final ToolExample ex = ToolExample.create(
                "createTask",
                null,
                0,
                "Create a task called review-code",
                "I'll create that task for you.",
                "createTask(name='review-code', description='Review the code')",
                "Task 'review-code' created successfully.");

        when(toolExampleRepository.findAllGlobal()).thenReturn(List.of(ex));

        final String result = provider.loadExamples(null);

        assertThat(result).contains("# Tool Calling Examples");
        assertThat(result).contains("## Tool: createTask");
        assertThat(result).contains("<example>");
        assertThat(result).contains("User: Create a task called review-code");
        assertThat(result).contains("Assistant: I'll create that task for you.");
        assertThat(result).contains("Tool call: createTask(name='review-code'");
        assertThat(result).contains("Tool result: Task 'review-code' created successfully.");
        assertThat(result).contains("</example>");
    }

    @Test
    @DisplayName("loadExamples(userId): loads user-specific + global examples")
    void loadExamples_withUserId_loadsUserExamples() {
        final String userId = "user-123";
        final ToolExample globalEx = ToolExample.create(
                "listSkills",
                null,
                0,
                "What skills are available?",
                null,
                "listSkills()",
                "Available skills: code-reviewer, summarizer");
        final ToolExample userEx = ToolExample.create(
                "listSkills",
                userId,
                1,
                "Show me my skills",
                "Let me check your skills.",
                "listSkills()",
                "Your skills: custom-tool");

        when(toolExampleRepository.findAllForUser(userId)).thenReturn(List.of(globalEx, userEx));

        final String result = provider.loadExamples(userId);

        assertThat(result).contains("## Tool: listSkills");
        assertThat(result).contains("What skills are available?");
        assertThat(result).contains("Show me my skills");
    }

    @Test
    @DisplayName("loadExamples(): groups examples by tool name")
    void loadExamples_groupsByToolName() {
        final ToolExample taskEx =
                ToolExample.create("createTask", null, 0, "Create a task", null, "createTask(name='test')", null);
        final ToolExample skillEx =
                ToolExample.create("listSkills", null, 0, "List skills", null, "listSkills()", null);

        when(toolExampleRepository.findAllGlobal()).thenReturn(List.of(taskEx, skillEx));

        final String result = provider.loadExamples(null);

        assertThat(result).contains("## Tool: createTask");
        assertThat(result).contains("## Tool: listSkills");
    }

    @Test
    @DisplayName("loadExamples(): omits blank assistant message and tool result")
    void loadExamples_omitsBlankOptionalFields() {
        final ToolExample ex = ToolExample.create("listSkills", null, 0, "Show skills", null, "listSkills()", null);

        when(toolExampleRepository.findAllGlobal()).thenReturn(List.of(ex));

        final String result = provider.loadExamples(null);

        assertThat(result).doesNotContain("Assistant:");
        assertThat(result).doesNotContain("Tool result:");
        assertThat(result).contains("User: Show skills");
        assertThat(result).contains("Tool call: listSkills()");
    }

    @Test
    @DisplayName("loadExamples(): gracefully handles repository exception")
    void loadExamples_dbError_returnsEmpty() {
        when(toolExampleRepository.findAllGlobal()).thenThrow(new RuntimeException("DB down"));

        final String result = provider.loadExamples(null);

        assertThat(result).isEmpty();
    }
}
