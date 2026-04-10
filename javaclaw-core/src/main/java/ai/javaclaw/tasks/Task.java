package ai.javaclaw.tasks;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

@Table("tasks")
public class Task {

    public enum Status {
        todo,
        in_progress,
        completed,
        failed,
        cancelled,
        awaiting_human_input
    }

    @Id
    private final String id;

    private final String name;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Status status;
    private final String description;
    private final String feedback;

    /** @deprecated Use {@link #conversationId} for routing. Kept for backward compatibility with existing tasks. */
    @Deprecated
    private final String sourceChannelName;

    private final String conversationId;

    private final String parentTaskId;
    private final NotifyPolicy notifyPolicy;
    private final TaskRuntime runtimeType;
    private final Integer timeoutSeconds;
    private final Boolean carryOverContext;
    private final String userId;
    private final Instant failedAt;
    private final Instant cancelledAt;

    @Version
    private final Integer version;

    @PersistenceCreator
    public Task(
            String id,
            String name,
            Instant createdAt,
            Instant updatedAt,
            Status status,
            String description,
            String feedback,
            String sourceChannelName,
            String conversationId,
            String parentTaskId,
            NotifyPolicy notifyPolicy,
            TaskRuntime runtimeType,
            Integer timeoutSeconds,
            Boolean carryOverContext,
            String userId,
            Instant failedAt,
            Instant cancelledAt,
            Integer version) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.status = status;
        this.description = description;
        this.feedback = feedback;
        this.sourceChannelName = sourceChannelName;
        this.conversationId = conversationId;
        this.parentTaskId = parentTaskId;
        this.notifyPolicy = notifyPolicy;
        this.runtimeType = runtimeType;
        this.timeoutSeconds = timeoutSeconds;
        this.carryOverContext = carryOverContext;
        this.userId = userId;
        this.failedAt = failedAt;
        this.cancelledAt = cancelledAt;
        this.version = version;
    }

    /** Backward-compatible constructor for existing code. New fields default to null. */
    public Task(
            String id,
            String name,
            Instant createdAt,
            Instant updatedAt,
            Status status,
            String description,
            String feedback,
            String sourceChannelName,
            String conversationId) {
        this(
                id,
                name,
                createdAt,
                updatedAt,
                status,
                description,
                feedback,
                sourceChannelName,
                conversationId,
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

    public static Task newTask(final String name, final String description) {
        return new Task(null, name, Instant.now(), Instant.now(), Status.todo, description, null, null, null);
    }

    public static Task newTask(final String name, final Instant createdAt, final String description) {
        return new Task(null, name, createdAt, Instant.now(), Status.todo, description, null, null, null);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Status getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public String getFeedback() {
        return feedback;
    }

    /** @deprecated Use {@link #getConversationId()} for routing. */
    @Deprecated
    public String getSourceChannelName() {
        return sourceChannelName;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getParentTaskId() {
        return parentTaskId;
    }

    public NotifyPolicy getNotifyPolicy() {
        return notifyPolicy;
    }

    public TaskRuntime getRuntimeType() {
        return runtimeType;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public Boolean getCarryOverContext() {
        return carryOverContext;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Integer getVersion() {
        return version;
    }

    public Task withVersion(final Integer version) {
        return new Task(
                id,
                name,
                createdAt,
                updatedAt,
                status,
                description,
                feedback,
                sourceChannelName,
                conversationId,
                parentTaskId,
                notifyPolicy,
                runtimeType,
                timeoutSeconds,
                carryOverContext,
                userId,
                failedAt,
                cancelledAt,
                version);
    }

    public Task withStatus(final Status newStatus) {
        Instant newFailedAt = newStatus == Status.failed ? Instant.now() : this.failedAt;
        Instant newCancelledAt = newStatus == Status.cancelled ? Instant.now() : this.cancelledAt;
        return new Task(
                id,
                name,
                createdAt,
                Instant.now(),
                newStatus,
                description,
                feedback,
                sourceChannelName,
                conversationId,
                parentTaskId,
                notifyPolicy,
                runtimeType,
                timeoutSeconds,
                carryOverContext,
                userId,
                newFailedAt,
                newCancelledAt,
                version);
    }

    public Task withFeedback(final String feedback) {
        return new Task(
                id,
                name,
                createdAt,
                Instant.now(),
                status,
                description,
                feedback,
                sourceChannelName,
                conversationId,
                parentTaskId,
                notifyPolicy,
                runtimeType,
                timeoutSeconds,
                carryOverContext,
                userId,
                failedAt,
                cancelledAt,
                version);
    }

    /** @deprecated Use {@link #withConversationId(String)} instead. */
    @Deprecated
    public Task withSourceChannelName(final String channelName) {
        return new Task(
                id,
                name,
                createdAt,
                Instant.now(),
                status,
                description,
                feedback,
                channelName,
                conversationId,
                parentTaskId,
                notifyPolicy,
                runtimeType,
                timeoutSeconds,
                carryOverContext,
                userId,
                failedAt,
                cancelledAt,
                version);
    }

    public Task withConversationId(final String conversationId) {
        return new Task(
                id,
                name,
                createdAt,
                Instant.now(),
                status,
                description,
                feedback,
                sourceChannelName,
                conversationId,
                parentTaskId,
                notifyPolicy,
                runtimeType,
                timeoutSeconds,
                carryOverContext,
                userId,
                failedAt,
                cancelledAt,
                version);
    }

    public Task withParentTaskId(final String parentTaskId) {
        return new Task(
                id,
                name,
                createdAt,
                Instant.now(),
                status,
                description,
                feedback,
                sourceChannelName,
                conversationId,
                parentTaskId,
                notifyPolicy,
                runtimeType,
                timeoutSeconds,
                carryOverContext,
                userId,
                failedAt,
                cancelledAt,
                version);
    }

    public Task withNotifyPolicy(final NotifyPolicy notifyPolicy) {
        return new Task(
                id,
                name,
                createdAt,
                Instant.now(),
                status,
                description,
                feedback,
                sourceChannelName,
                conversationId,
                parentTaskId,
                notifyPolicy,
                runtimeType,
                timeoutSeconds,
                carryOverContext,
                userId,
                failedAt,
                cancelledAt,
                version);
    }

    public Task withRuntimeType(final TaskRuntime runtimeType) {
        return new Task(
                id,
                name,
                createdAt,
                Instant.now(),
                status,
                description,
                feedback,
                sourceChannelName,
                conversationId,
                parentTaskId,
                notifyPolicy,
                runtimeType,
                timeoutSeconds,
                carryOverContext,
                userId,
                failedAt,
                cancelledAt,
                version);
    }

    public Task withTimeoutSeconds(final Integer timeoutSeconds) {
        return new Task(
                id,
                name,
                createdAt,
                Instant.now(),
                status,
                description,
                feedback,
                sourceChannelName,
                conversationId,
                parentTaskId,
                notifyPolicy,
                runtimeType,
                timeoutSeconds,
                carryOverContext,
                userId,
                failedAt,
                cancelledAt,
                version);
    }

    public Task withCarryOverContext(final Boolean carryOverContext) {
        return new Task(
                id,
                name,
                createdAt,
                Instant.now(),
                status,
                description,
                feedback,
                sourceChannelName,
                conversationId,
                parentTaskId,
                notifyPolicy,
                runtimeType,
                timeoutSeconds,
                carryOverContext,
                userId,
                failedAt,
                cancelledAt,
                version);
    }

    public Task withUserId(final String userId) {
        return new Task(
                id,
                name,
                createdAt,
                Instant.now(),
                status,
                description,
                feedback,
                sourceChannelName,
                conversationId,
                parentTaskId,
                notifyPolicy,
                runtimeType,
                timeoutSeconds,
                carryOverContext,
                userId,
                failedAt,
                cancelledAt,
                version);
    }

    @Override
    public String toString() {
        return "Task '" + name + "'";
    }
}
