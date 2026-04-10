package ai.javaclaw.agent.pipeline;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Provides formatted few-shot examples for tool calling, loaded from the database.
 *
 * <p>Examples are grouped by tool name and rendered as XML-style example blocks
 * suitable for inclusion in the system prompt. This improves tool-calling accuracy,
 * especially for models like GigaChat that benefit from explicit examples.
 *
 * <p>Format per tool:
 * <pre>
 * ## Tool Examples: &lt;toolName&gt;
 *
 * &lt;example&gt;
 * User: &lt;userMessage&gt;
 * Assistant: &lt;assistantMessage&gt;
 * Tool call: &lt;toolCall&gt;
 * Tool result: &lt;toolResult&gt;
 * &lt;/example&gt;
 * </pre>
 */
@Component
public class FewShotExamplesProvider {

    private static final Logger log = LoggerFactory.getLogger(FewShotExamplesProvider.class);

    private final ToolExampleRepository toolExampleRepository;

    public FewShotExamplesProvider(final ToolExampleRepository toolExampleRepository) {
        Assert.notNull(toolExampleRepository, "toolExampleRepository must not be null");
        this.toolExampleRepository = toolExampleRepository;
    }

    /**
     * Loads all few-shot examples visible to the given user (global + per-user)
     * and formats them as a prompt section.
     *
     * @param userId user id for per-user examples, or {@code null} for global-only
     * @return formatted examples string, or empty string if none exist
     */
    public String loadExamples(@Nullable final String userId) {
        final List<ToolExample> examples;
        try {
            examples = (userId != null)
                    ? toolExampleRepository.findAllForUser(userId)
                    : toolExampleRepository.findAllGlobal();
        } catch (final Exception e) {
            log.warn("FewShotExamplesProvider: could not load examples — {}", e.getMessage());
            return "";
        }

        if (examples == null || examples.isEmpty()) {
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("# Tool Calling Examples\n\n");

        String currentTool = null;
        for (final ToolExample ex : examples) {
            if (!ex.toolName().equals(currentTool)) {
                currentTool = ex.toolName();
                sb.append("## Tool: ").append(currentTool).append("\n\n");
            }

            sb.append("<example>\n");
            sb.append("User: ").append(ex.userMessage()).append("\n");
            if (ex.assistantMessage() != null && !ex.assistantMessage().isBlank()) {
                sb.append("Assistant: ").append(ex.assistantMessage()).append("\n");
            }
            sb.append("Tool call: ").append(ex.toolCall()).append("\n");
            if (ex.toolResult() != null && !ex.toolResult().isBlank()) {
                sb.append("Tool result: ").append(ex.toolResult()).append("\n");
            }
            sb.append("</example>\n\n");
        }

        return sb.toString().strip();
    }
}
