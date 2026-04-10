package ai.javaclaw.agent.pipeline;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Persisted few-shot example for a specific tool, used to improve tool-calling accuracy.
 *
 * @param id               auto-generated UUID
 * @param toolName         name of the tool this example demonstrates
 * @param ownerId          user id for per-user examples, or {@code null} for global
 * @param exampleOrder     ordering index within the same tool (lower = first)
 * @param userMessage      example user message that triggers the tool call
 * @param assistantMessage optional assistant reasoning before the tool call
 * @param toolCall         example tool invocation (name + parameters)
 * @param toolResult       optional expected tool result
 */
@Table("tool_examples")
public record ToolExample(
        @Id String id,
        @Column("tool_name") String toolName,
        @Column("owner_id") String ownerId,
        @Column("example_order") int exampleOrder,
        @Column("user_message") String userMessage,
        @Column("assistant_message") String assistantMessage,
        @Column("tool_call") String toolCall,
        @Column("tool_result") String toolResult) {

    public static ToolExample create(
            String toolName,
            String ownerId,
            int exampleOrder,
            String userMessage,
            String assistantMessage,
            String toolCall,
            String toolResult) {
        return new ToolExample(
                null, toolName, ownerId, exampleOrder, userMessage, assistantMessage, toolCall, toolResult);
    }
}
