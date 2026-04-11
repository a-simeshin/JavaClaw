package ai.javaclaw.agent.audit;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ChatAuditService {

    private final ChatAuditLogRepository repository;

    public ChatAuditService(final ChatAuditLogRepository repository) {
        this.repository = repository;
    }

    @Async
    public void logSuccess(
            final String conversationId,
            final String method,
            final Prompt prompt,
            final String responseText,
            final long durationMs) {
        logSuccess(conversationId, method, prompt, responseText, durationMs, null, null, null);
    }

    @Async
    public void logSuccess(
            final String conversationId,
            final String method,
            final Prompt prompt,
            final String responseText,
            final long durationMs,
            final String userId,
            final String toolCallsDetail,
            final String tokenUsage) {
        repository.save(buildEntry(
                conversationId,
                method,
                prompt,
                responseText,
                null,
                null,
                durationMs,
                userId,
                toolCallsDetail,
                tokenUsage));
    }

    @Async
    public void logError(
            final String conversationId,
            final String method,
            final Prompt prompt,
            final Throwable error,
            final long durationMs) {
        logError(conversationId, method, prompt, error, durationMs, null, null, null);
    }

    @Async
    public void logError(
            final String conversationId,
            final String method,
            final Prompt prompt,
            final Throwable error,
            final long durationMs,
            final String userId,
            final String toolCallsDetail,
            final String tokenUsage) {
        final StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        repository.save(buildEntry(
                conversationId,
                method,
                prompt,
                null,
                error.getMessage(),
                sw.toString(),
                durationMs,
                userId,
                toolCallsDetail,
                tokenUsage));
    }

    /**
     * Unified log method with structured {@link Usage} and {@link AssistantMessage.ToolCall} objects.
     * Serializes usage and toolCalls to JSON strings before persisting.
     */
    @Async
    public void log(
            final String conversationId,
            final String method,
            final Prompt prompt,
            final String responseText,
            final long durationMs,
            @Nullable final String userId,
            @Nullable final Usage usage,
            @Nullable final List<AssistantMessage.ToolCall> toolCalls) {
        final String tokenUsageJson = serializeUsage(usage);
        final String toolCallsJson = serializeToolCalls(toolCalls);
        logSuccess(conversationId, method, prompt, responseText, durationMs, userId, toolCallsJson, tokenUsageJson);
    }

    /**
     * Unified logError method with structured Throwable and optional userId.
     */
    @Async
    public void logError(
            final String conversationId,
            final String method,
            final Prompt prompt,
            final Throwable error,
            final long durationMs,
            @Nullable final String userId) {
        logError(conversationId, method, prompt, error, durationMs, userId, null, null);
    }

    private static String serializeUsage(@Nullable final Usage usage) {
        if (usage == null) {
            return null;
        }
        return "{\"promptTokens\":"
                + usage.getPromptTokens()
                + ",\"completionTokens\":"
                + usage.getCompletionTokens()
                + ",\"totalTokens\":"
                + usage.getTotalTokens()
                + "}";
    }

    private static String serializeToolCalls(@Nullable final List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }
        final StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < toolCalls.size(); i++) {
            final AssistantMessage.ToolCall tc = toolCalls.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"id\":\"")
                    .append(escape(tc.id()))
                    .append("\",\"name\":\"")
                    .append(escape(tc.name()))
                    .append("\",\"arguments\":")
                    .append(tc.arguments() != null ? tc.arguments() : "null")
                    .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escape(@Nullable final String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private ChatAuditLog buildEntry(
            final String conversationId,
            final String method,
            final Prompt prompt,
            final String responseText,
            final String errorMessage,
            final String errorTrace,
            final long durationMs,
            final String userId,
            final String toolCallsDetail,
            final String tokenUsage) {

        String systemPrompt = null;
        final StringBuilder history = new StringBuilder();
        String userContent = null;

        if (prompt != null) {
            for (final Message msg : prompt.getInstructions()) {
                if (msg instanceof SystemMessage sm) {
                    systemPrompt = sm.getText();
                } else {
                    final String type = msg.getMessageType().name();
                    final String text = msg.getText();
                    if ("USER".equals(type)) {
                        userContent = text;
                    }
                    history.append("[").append(type).append("] ").append(text).append("\n---\n");
                }
            }
        }

        String toolNames = null;
        if (prompt != null && prompt.getOptions() instanceof ToolCallingChatOptions opts) {
            final List<ToolCallback> callbacks = opts.getToolCallbacks();
            if (callbacks != null && !callbacks.isEmpty()) {
                toolNames = callbacks.stream()
                        .map(tc -> tc.getToolDefinition().name())
                        .collect(Collectors.joining(", "));
            }
        }

        return new ChatAuditLog(
                null,
                conversationId,
                Instant.now(),
                method,
                systemPrompt,
                history.isEmpty() ? null : history.toString(),
                userContent != null ? userContent : "",
                toolNames,
                responseText,
                errorMessage,
                errorTrace,
                durationMs,
                userId,
                toolCallsDetail,
                tokenUsage);
    }
}
