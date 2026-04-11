package ai.javaclaw.api.admin.toolexamples;

import jakarta.validation.constraints.NotBlank;
import org.springframework.lang.Nullable;

/**
 * Request payload for creating/updating a few-shot tool example.
 */
public record ToolExampleRequest(
        @NotBlank String toolName,
        @Nullable String ownerId,
        int exampleOrder,
        @NotBlank String userMessage,
        @Nullable String assistantMessage,
        @NotBlank String toolCall,
        @Nullable String toolResult) {}
