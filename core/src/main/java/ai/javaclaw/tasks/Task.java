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
    private final String sourceChannelName;

    public Task(
            String id,
            String name,
            Instant createdAt,
            Instant updatedAt,
            Status status,
            String description,
            String feedback,
            String sourceChannelName) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.status = status;
        this.description = description;
        this.feedback = feedback;
        this.sourceChannelName = sourceChannelName;
    }

    public static Task newTask(String name, String description) {
        return new Task(null, name, Instant.now(), Instant.now(), Status.todo, description, null, null);
    }

    public static Task newTask(String name, Instant createdAt, String description) {
        return new Task(null, name, createdAt, Instant.now(), Status.todo, description, null, null);
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

    public String getSourceChannelName() {
        return sourceChannelName;
    }

    public Task withStatus(Status newStatus) {
        return new Task(id, name, createdAt, Instant.now(), newStatus, description, feedback, sourceChannelName);
    }

    public Task withFeedback(String feedback) {
        return new Task(id, name, createdAt, Instant.now(), status, description, feedback, sourceChannelName);
    }

    public Task withSourceChannelName(String channelName) {
        return new Task(id, name, createdAt, Instant.now(), status, description, feedback, channelName);
    }

    @Override
    public String toString() {
        return "Task '" + name + "'";
    }
}
