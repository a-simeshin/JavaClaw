package ai.javaclaw.agent.audit;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Immutable audit record for task lifecycle events. Each row captures one event
 * in the task execution timeline: creation, start, tool calls, progress updates,
 * approval requests/responses, completion, failure, cancellation, timeout, or delivery.
 *
 * <p>Users can query this table (via AuditTool) to get full transparency into
 * what happened during a task execution: which prompts were sent, which tools
 * were called, what the model responded, and any errors that occurred.
 *
 * <p>Uses auto-generated BIGINT identity (not UUID) since audit records are
 * append-only and never referenced by other entities.
 */
@Table("task_audit_log")
public record TaskAuditLog(
        @Id Long id,
        @Column("task_id") String taskId,
        @Column("execution_id") String executionId,
        @Column("event_type") String eventType,
        @Column("created_at") Instant createdAt,
        @Column("system_prompt") String systemPrompt,
        @Column("user_prompt") String userPrompt,
        @Column("tool_name") String toolName,
        @Column("tool_args") String toolArgs,
        @Column("tool_result") String toolResult,
        @Column("tool_duration_ms") Long toolDurationMs,
        @Column("llm_request") String llmRequest,
        @Column("llm_response") String llmResponse,
        @Column("token_usage") String tokenUsage,
        @Column("error_message") String errorMessage,
        @Column("error_trace") String errorTrace,
        @Column("duration_ms") Long durationMs,
        @Column("metadata") String metadata) {

    /**
     * Valid event types for the task audit log.
     */
    public static final String EVENT_CREATED = "created";

    public static final String EVENT_STARTED = "started";
    public static final String EVENT_TOOL_CALL = "tool_call";
    public static final String EVENT_PROGRESS = "progress";
    public static final String EVENT_APPROVAL_REQUESTED = "approval_requested";
    public static final String EVENT_APPROVAL_RECEIVED = "approval_received";
    public static final String EVENT_COMPLETED = "completed";
    public static final String EVENT_FAILED = "failed";
    public static final String EVENT_CANCELLED = "cancelled";
    public static final String EVENT_TIMEOUT = "timeout";
    public static final String EVENT_DELIVERED = "delivered";

    /** Creates a task-level audit event (created, started, completed, failed, cancelled, timeout). */
    public static TaskAuditLog taskEvent(String taskId, String executionId, String eventType) {
        return new TaskAuditLog(
                null,
                taskId,
                executionId,
                eventType,
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /** Creates a task event with error details. */
    public static TaskAuditLog errorEvent(
            String taskId, String executionId, String errorMessage, String errorTrace, Long durationMs) {
        return new TaskAuditLog(
                null,
                taskId,
                executionId,
                EVENT_FAILED,
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                errorMessage,
                errorTrace,
                durationMs,
                null);
    }

    /** Creates an LLM request/response audit event with prompts and response. */
    public static TaskAuditLog llmEvent(
            String taskId,
            String executionId,
            String systemPrompt,
            String userPrompt,
            String llmRequest,
            String llmResponse,
            String tokenUsage,
            Long durationMs) {
        return new TaskAuditLog(
                null,
                taskId,
                executionId,
                EVENT_STARTED,
                Instant.now(),
                systemPrompt,
                userPrompt,
                null,
                null,
                null,
                null,
                llmRequest,
                llmResponse,
                tokenUsage,
                null,
                null,
                durationMs,
                null);
    }

    /** Creates a tool call audit event. */
    public static TaskAuditLog toolCallEvent(
            String taskId,
            String executionId,
            String toolName,
            String toolArgs,
            String toolResult,
            Long toolDurationMs) {
        return new TaskAuditLog(
                null,
                taskId,
                executionId,
                EVENT_TOOL_CALL,
                Instant.now(),
                null,
                null,
                toolName,
                toolArgs,
                toolResult,
                toolDurationMs,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /** Creates a progress update audit event. */
    public static TaskAuditLog progressEvent(String taskId, String executionId, String metadata) {
        return new TaskAuditLog(
                null,
                taskId,
                executionId,
                EVENT_PROGRESS,
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                metadata);
    }

    /** Creates an approval-related audit event (requested or received). */
    public static TaskAuditLog approvalEvent(String taskId, String executionId, String eventType, String metadata) {
        return new TaskAuditLog(
                null,
                taskId,
                executionId,
                eventType,
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                metadata);
    }
}
