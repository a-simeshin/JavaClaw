package ai.javaclaw.tasks;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Represents a human-in-the-loop approval request. When a task needs user permission
 * to proceed (e.g. "Buy ticket for 9500?"), an ApprovalRequest is created and the task
 * blocks until the user responds or the request times out.
 */
@Table("approval_requests")
public class ApprovalRequest {

    public enum Status {
        pending,
        approved,
        denied,
        timeout
    }

    @Id
    private final String id;

    private final String taskId;
    private final String conversationId;
    private final String question;
    private final String response;
    private final Status status;
    private final Instant timeoutAt;
    private final Instant createdAt;
    private final Instant respondedAt;

    @PersistenceCreator
    public ApprovalRequest(
            String id,
            String taskId,
            String conversationId,
            String question,
            String response,
            Status status,
            Instant timeoutAt,
            Instant createdAt,
            Instant respondedAt) {
        this.id = id;
        this.taskId = taskId;
        this.conversationId = conversationId;
        this.question = question;
        this.response = response;
        this.status = status;
        this.timeoutAt = timeoutAt;
        this.createdAt = createdAt;
        this.respondedAt = respondedAt;
    }

    public static ApprovalRequest create(String taskId, String conversationId, String question, Instant timeoutAt) {
        Instant now = Instant.now();
        return new ApprovalRequest(null, taskId, conversationId, question, null, Status.pending, timeoutAt, now, null);
    }

    public ApprovalRequest withApproved(String userResponse) {
        return new ApprovalRequest(
                id,
                taskId,
                conversationId,
                question,
                userResponse,
                Status.approved,
                timeoutAt,
                createdAt,
                Instant.now());
    }

    public ApprovalRequest withDenied(String userResponse) {
        return new ApprovalRequest(
                id, taskId, conversationId, question, userResponse, Status.denied, timeoutAt, createdAt, Instant.now());
    }

    public ApprovalRequest withTimedOut() {
        return new ApprovalRequest(
                id, taskId, conversationId, question, null, Status.timeout, timeoutAt, createdAt, Instant.now());
    }

    public String getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getQuestion() {
        return question;
    }

    public String getResponse() {
        return response;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getTimeoutAt() {
        return timeoutAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public boolean isPending() {
        return status == Status.pending;
    }

    public boolean isExpired() {
        return status == Status.pending && Instant.now().isAfter(timeoutAt);
    }

    @Override
    public String toString() {
        return "ApprovalRequest{taskId='" + taskId + "', status=" + status + ", question='"
                + (question != null && question.length() > 50 ? question.substring(0, 50) + "..." : question) + "'}";
    }
}
