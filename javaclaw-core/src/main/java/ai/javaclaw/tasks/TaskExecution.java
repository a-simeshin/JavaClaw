package ai.javaclaw.tasks;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Records a single execution of a {@link Task}. For one-shot tasks there is typically one execution;
 * recurring (cron) tasks accumulate one execution per firing. Stores the full LLM
 * request/response, tool calls, token usage, and error details for audit purposes.
 */
@Table("task_executions")
public class TaskExecution {

    public enum Status {
        running,
        completed,
        failed,
        cancelled
    }

    @Id
    private final String id;

    private final String taskId;
    private final Integer executionNumber;
    private final Status status;
    private final String systemPrompt;
    private final String userPrompt;
    private final String llmResponse;
    private final String toolCalls;
    private final String tokenUsage;
    private final String errorMessage;
    private final String errorTrace;
    private final Instant startedAt;
    private final Instant completedAt;
    private final Long durationMs;
    private final Instant createdAt;

    @PersistenceCreator
    public TaskExecution(
            String id,
            String taskId,
            Integer executionNumber,
            Status status,
            String systemPrompt,
            String userPrompt,
            String llmResponse,
            String toolCalls,
            String tokenUsage,
            String errorMessage,
            String errorTrace,
            Instant startedAt,
            Instant completedAt,
            Long durationMs,
            Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.executionNumber = executionNumber;
        this.status = status;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.llmResponse = llmResponse;
        this.toolCalls = toolCalls;
        this.tokenUsage = tokenUsage;
        this.errorMessage = errorMessage;
        this.errorTrace = errorTrace;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.durationMs = durationMs;
        this.createdAt = createdAt;
    }

    public static TaskExecution start(String taskId, int executionNumber, String systemPrompt, String userPrompt) {
        Instant now = Instant.now();
        return new TaskExecution(
                null,
                taskId,
                executionNumber,
                Status.running,
                systemPrompt,
                userPrompt,
                null,
                null,
                null,
                null,
                null,
                now,
                null,
                null,
                now);
    }

    public String getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public Integer getExecutionNumber() {
        return executionNumber;
    }

    public Status getStatus() {
        return status;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public String getLlmResponse() {
        return llmResponse;
    }

    public String getToolCalls() {
        return toolCalls;
    }

    public String getTokenUsage() {
        return tokenUsage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getErrorTrace() {
        return errorTrace;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public TaskExecution withCompleted(String llmResponse, String toolCalls, String tokenUsage) {
        Instant now = Instant.now();
        long duration = startedAt != null ? now.toEpochMilli() - startedAt.toEpochMilli() : 0;
        return new TaskExecution(
                id,
                taskId,
                executionNumber,
                Status.completed,
                systemPrompt,
                userPrompt,
                llmResponse,
                toolCalls,
                tokenUsage,
                null,
                null,
                startedAt,
                now,
                duration,
                createdAt);
    }

    public TaskExecution withFailed(String errorMessage, String errorTrace) {
        Instant now = Instant.now();
        long duration = startedAt != null ? now.toEpochMilli() - startedAt.toEpochMilli() : 0;
        return new TaskExecution(
                id,
                taskId,
                executionNumber,
                Status.failed,
                systemPrompt,
                userPrompt,
                llmResponse,
                toolCalls,
                tokenUsage,
                errorMessage,
                errorTrace,
                startedAt,
                now,
                duration,
                createdAt);
    }

    public TaskExecution withCancelled() {
        Instant now = Instant.now();
        long duration = startedAt != null ? now.toEpochMilli() - startedAt.toEpochMilli() : 0;
        return new TaskExecution(
                id,
                taskId,
                executionNumber,
                Status.cancelled,
                systemPrompt,
                userPrompt,
                llmResponse,
                toolCalls,
                tokenUsage,
                null,
                null,
                startedAt,
                now,
                duration,
                createdAt);
    }

    @Override
    public String toString() {
        return "TaskExecution{taskId='" + taskId + "', #" + executionNumber + ", status=" + status + "}";
    }
}
