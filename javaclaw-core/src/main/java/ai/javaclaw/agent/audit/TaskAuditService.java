package ai.javaclaw.agent.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for recording task lifecycle events to the task_audit_log table.
 * Follows the same async, non-blocking pattern as {@link ChatAuditService}.
 *
 * <p>Each method corresponds to a specific event type in the task execution
 * timeline. TaskHandler calls these methods at key points during execution,
 * and AuditTool queries the resulting records for user transparency (S20).
 */
@Service
public class TaskAuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskAuditService.class);

    private final TaskAuditLogRepository repository;

    public TaskAuditService(final TaskAuditLogRepository repository) {
        this.repository = repository;
    }

    @Async
    public void logCreated(final String taskId) {
        save(TaskAuditLog.taskEvent(taskId, null, TaskAuditLog.EVENT_CREATED));
    }

    @Async
    public void logStarted(final String taskId, final String executionId) {
        save(TaskAuditLog.taskEvent(taskId, executionId, TaskAuditLog.EVENT_STARTED));
    }

    @Async
    public void logCompleted(final String taskId, final String executionId, final Long durationMs) {
        save(new TaskAuditLog(
                null,
                taskId,
                executionId,
                TaskAuditLog.EVENT_COMPLETED,
                java.time.Instant.now(),
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
                durationMs,
                null));
    }

    @Async
    public void logFailed(
            final String taskId,
            final String executionId,
            final String errorMessage,
            final String errorTrace,
            final Long durationMs) {
        save(TaskAuditLog.errorEvent(taskId, executionId, errorMessage, errorTrace, durationMs));
    }

    @Async
    public void logCancelled(final String taskId, final String executionId) {
        save(TaskAuditLog.taskEvent(taskId, executionId, TaskAuditLog.EVENT_CANCELLED));
    }

    @Async
    public void logLlmCall(
            final String taskId,
            final String executionId,
            final String systemPrompt,
            final String userPrompt,
            final String llmResponse,
            final String tokenUsage,
            final Long durationMs) {
        save(TaskAuditLog.llmEvent(
                taskId, executionId, systemPrompt, userPrompt, null, llmResponse, tokenUsage, durationMs));
    }

    @Async
    public void logToolCall(
            final String taskId,
            final String executionId,
            final String toolName,
            final String toolArgs,
            final String toolResult,
            final Long toolDurationMs) {
        save(TaskAuditLog.toolCallEvent(taskId, executionId, toolName, toolArgs, toolResult, toolDurationMs));
    }

    @Async
    public void logProgress(final String taskId, final String executionId, final String metadata) {
        save(TaskAuditLog.progressEvent(taskId, executionId, metadata));
    }

    @Async
    public void logApprovalRequested(final String taskId, final String executionId, final String metadata) {
        save(TaskAuditLog.approvalEvent(taskId, executionId, TaskAuditLog.EVENT_APPROVAL_REQUESTED, metadata));
    }

    @Async
    public void logApprovalReceived(final String taskId, final String executionId, final String metadata) {
        save(TaskAuditLog.approvalEvent(taskId, executionId, TaskAuditLog.EVENT_APPROVAL_RECEIVED, metadata));
    }

    @Async
    public void logTimeout(final String taskId, final String executionId) {
        save(TaskAuditLog.taskEvent(taskId, executionId, TaskAuditLog.EVENT_TIMEOUT));
    }

    @Async
    public void logDelivered(final String taskId, final String executionId) {
        save(TaskAuditLog.taskEvent(taskId, executionId, TaskAuditLog.EVENT_DELIVERED));
    }

    private void save(final TaskAuditLog entry) {
        try {
            repository.save(entry);
        } catch (Exception e) {
            LOGGER.error(
                    "Failed to save task audit log entry [taskId={}, type={}]: {}",
                    entry.taskId(),
                    entry.eventType(),
                    e.getMessage(),
                    e);
        }
    }
}
