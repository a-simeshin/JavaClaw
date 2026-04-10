package ai.javaclaw.tools;

import ai.javaclaw.agent.audit.ChatAuditLog;
import ai.javaclaw.agent.audit.ChatAuditLogRepository;
import ai.javaclaw.agent.audit.DeliveryAuditLog;
import ai.javaclaw.agent.audit.DeliveryAuditLogRepository;
import ai.javaclaw.agent.audit.TaskAuditLog;
import ai.javaclaw.agent.audit.TaskAuditLogRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Provides agent-accessible audit tools for full transparency into task execution,
 * chat interactions, and notification delivery. Users can ask the agent questions like
 * "What happened with task X?" and the agent calls these tools to retrieve detailed audit data.
 */
public class AuditTool {

    private static final Logger logger = LoggerFactory.getLogger(AuditTool.class);
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final TaskAuditLogRepository taskAuditLogRepository;
    private final ChatAuditLogRepository chatAuditLogRepository;
    private final DeliveryAuditLogRepository deliveryAuditLogRepository;

    public AuditTool(
            TaskAuditLogRepository taskAuditLogRepository,
            ChatAuditLogRepository chatAuditLogRepository,
            DeliveryAuditLogRepository deliveryAuditLogRepository) {
        this.taskAuditLogRepository = taskAuditLogRepository;
        this.chatAuditLogRepository = chatAuditLogRepository;
        this.deliveryAuditLogRepository = deliveryAuditLogRepository;
    }

    @Tool(
            description =
                    """
            Retrieves the full audit trail for a task execution.
            Use this when a user asks about what happened with a specific task,
            wants to see error details, LLM requests/responses, tool calls, or timing information.

            Returns a chronological timeline of all events: creation, start, LLM calls,
            tool executions, progress updates, approval requests/responses, completion/failure/cancellation,
            and delivery attempts.

            - taskId: The ID of the task to audit.
            """)
    public String getTaskAudit(String taskId) {
        try {
            List<TaskAuditLog> logs = taskAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
            if (logs.isEmpty()) {
                return String.format("No audit records found for task '%s'.", taskId);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Task Audit Trail for '")
                    .append(taskId)
                    .append("' (")
                    .append(logs.size())
                    .append(" events):\n\n");
            for (TaskAuditLog log : logs) {
                appendTaskAuditEntry(sb, log);
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to retrieve task audit for taskId={}", taskId, e);
            return "Error: Could not retrieve task audit. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Retrieves chat audit logs for a conversation showing LLM requests and responses.
            Use this when a user asks to see recent model interactions, token usage,
            tool calls detail, or wants to debug chat behavior.

            - conversationId: The conversation ID to query.
            - limit: Maximum number of records to return (most recent first). Use 5-10 for recent history.
            """)
    public String getChatAudit(String conversationId, int limit) {
        try {
            List<ChatAuditLog> logs = chatAuditLogRepository.findByConversationIdOrderByCreatedAtDesc(conversationId);
            if (logs.isEmpty()) {
                return String.format("No chat audit records found for conversation '%s'.", conversationId);
            }
            int effectiveLimit = Math.min(limit, logs.size());
            List<ChatAuditLog> limited = logs.subList(0, effectiveLimit);

            StringBuilder sb = new StringBuilder();
            sb.append("Chat Audit for conversation '")
                    .append(conversationId)
                    .append("' (showing ")
                    .append(effectiveLimit)
                    .append(" of ")
                    .append(logs.size())
                    .append(" records):\n\n");
            for (ChatAuditLog log : limited) {
                appendChatAuditEntry(sb, log);
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to retrieve chat audit for conversationId={}", conversationId, e);
            return "Error: Could not retrieve chat audit. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Retrieves delivery audit logs for a task showing all notification delivery attempts.
            Use this when a user asks about whether a notification was delivered,
            how many retry attempts were made, or what error occurred during delivery.

            - taskId: The ID of the task whose delivery history to query.
            """)
    public String getDeliveryAudit(String taskId) {
        try {
            List<DeliveryAuditLog> logs = deliveryAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
            if (logs.isEmpty()) {
                return String.format("No delivery records found for task '%s'.", taskId);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Delivery Audit for task '")
                    .append(taskId)
                    .append("' (")
                    .append(logs.size())
                    .append(" deliveries):\n\n");
            for (DeliveryAuditLog log : logs) {
                appendDeliveryAuditEntry(sb, log);
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to retrieve delivery audit for taskId={}", taskId, e);
            return "Error: Could not retrieve delivery audit. " + e.getMessage();
        }
    }

    private void appendTaskAuditEntry(StringBuilder sb, TaskAuditLog log) {
        sb.append("[").append(formatInstant(log.createdAt())).append("] ");
        sb.append(log.eventType().toUpperCase());
        if (log.durationMs() != null) {
            sb.append(" (").append(log.durationMs()).append("ms)");
        }
        sb.append("\n");

        if (log.systemPrompt() != null) {
            sb.append("  System prompt: ")
                    .append(truncate(log.systemPrompt(), 200))
                    .append("\n");
        }
        if (log.userPrompt() != null) {
            sb.append("  User prompt: ").append(truncate(log.userPrompt(), 200)).append("\n");
        }
        if (log.llmRequest() != null) {
            sb.append("  LLM request: ").append(truncate(log.llmRequest(), 300)).append("\n");
        }
        if (log.llmResponse() != null) {
            sb.append("  LLM response: ")
                    .append(truncate(log.llmResponse(), 300))
                    .append("\n");
        }
        if (log.toolName() != null) {
            sb.append("  Tool: ").append(log.toolName());
            if (log.toolDurationMs() != null) {
                sb.append(" (").append(log.toolDurationMs()).append("ms)");
            }
            sb.append("\n");
            if (log.toolArgs() != null) {
                sb.append("  Args: ").append(truncate(log.toolArgs(), 200)).append("\n");
            }
            if (log.toolResult() != null) {
                sb.append("  Result: ").append(truncate(log.toolResult(), 200)).append("\n");
            }
        }
        if (log.tokenUsage() != null) {
            sb.append("  Token usage: ").append(log.tokenUsage()).append("\n");
        }
        if (log.errorMessage() != null) {
            sb.append("  Error: ").append(truncate(log.errorMessage(), 300)).append("\n");
        }
        if (log.errorTrace() != null) {
            sb.append("  Stacktrace: ").append(truncate(log.errorTrace(), 500)).append("\n");
        }
        if (log.metadata() != null) {
            sb.append("  Metadata: ").append(truncate(log.metadata(), 200)).append("\n");
        }
        sb.append("\n");
    }

    private void appendChatAuditEntry(StringBuilder sb, ChatAuditLog log) {
        sb.append("[").append(formatInstant(log.createdAt())).append("] ");
        sb.append(log.method() != null ? log.method().toUpperCase() : "UNKNOWN");
        if (log.durationMs() != null) {
            sb.append(" (").append(log.durationMs()).append("ms)");
        }
        sb.append("\n");

        if (log.userContent() != null) {
            sb.append("  User: ").append(truncate(log.userContent(), 200)).append("\n");
        }
        if (log.responseText() != null) {
            sb.append("  Response: ").append(truncate(log.responseText(), 300)).append("\n");
        }
        if (log.toolNames() != null) {
            sb.append("  Tools used: ").append(log.toolNames()).append("\n");
        }
        if (log.toolCallsDetail() != null) {
            sb.append("  Tool calls detail: ")
                    .append(truncate(log.toolCallsDetail(), 300))
                    .append("\n");
        }
        if (log.tokenUsage() != null) {
            sb.append("  Token usage: ").append(log.tokenUsage()).append("\n");
        }
        if (log.errorMessage() != null) {
            sb.append("  Error: ").append(truncate(log.errorMessage(), 300)).append("\n");
        }
        sb.append("\n");
    }

    private void appendDeliveryAuditEntry(StringBuilder sb, DeliveryAuditLog log) {
        sb.append("[").append(formatInstant(log.createdAt())).append("] ");
        sb.append(log.status().toUpperCase());
        sb.append(" via ").append(log.channelName());
        if (log.durationMs() != null) {
            sb.append(" (").append(log.durationMs()).append("ms)");
        }
        sb.append("\n");

        sb.append("  Message: ").append(truncate(log.message(), 200)).append("\n");
        sb.append("  Attempts: ").append(log.attempts()).append("\n");
        if (log.errorMessage() != null) {
            sb.append("  Error: ").append(truncate(log.errorMessage(), 300)).append("\n");
        }
        sb.append("\n");
    }

    private String formatInstant(Instant instant) {
        return instant != null ? FORMATTER.format(instant) : "unknown";
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
