package ai.javaclaw.tasks;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("tasks")
public class Task {

    public enum Status {
        todo,
        in_progress,
        completed,
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
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.status = status;
        this.description = description;
        this.feedback = feedback;
        this.sourceChannelName = sourceChannelName;
        this.conversationId = conversationId;
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

    public Task withStatus(final Status newStatus) {
        return new Task(
                id,
                name,
                createdAt,
                Instant.now(),
                newStatus,
                description,
                feedback,
                sourceChannelName,
                conversationId);
    }

    public Task withFeedback(final String feedback) {
        return new Task(
                id, name, createdAt, Instant.now(), status, description, feedback, sourceChannelName, conversationId);
    }

    /** @deprecated Use {@link #withConversationId(String)} instead. */
    @Deprecated
    public Task withSourceChannelName(final String channelName) {
        return new Task(id, name, createdAt, Instant.now(), status, description, feedback, channelName, conversationId);
    }

    public Task withConversationId(final String conversationId) {
        return new Task(
                id, name, createdAt, Instant.now(), status, description, feedback, sourceChannelName, conversationId);
    }

    @Override
    public String toString() {
        return "Task '" + name + "'";
    }
}
