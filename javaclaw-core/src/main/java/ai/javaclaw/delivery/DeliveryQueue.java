package ai.javaclaw.delivery;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Represents a pending or completed delivery in the outbound notification queue.
 * Used for external channels (Telegram, Discord) where delivery requires network calls
 * and may need retry with exponential backoff. Web Chat uses SSE push directly
 * and does not go through this queue.
 *
 * <p>Multi-pod safety: {@code claimedBy} and {@code claimedAt} fields prevent duplicate
 * processing via {@code SELECT ... FOR UPDATE SKIP LOCKED} pattern.
 */
@Table("delivery_queue")
public class DeliveryQueue {

    public enum Status {
        pending,
        processing,
        delivered,
        failed
    }

    @Id
    private final String id;

    private final String taskId;
    private final String conversationId;
    private final String channelName;
    private final String message;
    private final Status status;
    private final int attempts;
    private final int maxAttempts;
    private final String errorMessage;
    private final String claimedBy;
    private final Instant claimedAt;
    private final Instant nextRetryAt;
    private final Instant createdAt;
    private final Instant completedAt;

    @PersistenceCreator
    public DeliveryQueue(
            String id,
            String taskId,
            String conversationId,
            String channelName,
            String message,
            Status status,
            int attempts,
            int maxAttempts,
            String errorMessage,
            String claimedBy,
            Instant claimedAt,
            Instant nextRetryAt,
            Instant createdAt,
            Instant completedAt) {
        this.id = id;
        this.taskId = taskId;
        this.conversationId = conversationId;
        this.channelName = channelName;
        this.message = message;
        this.status = status;
        this.attempts = attempts;
        this.maxAttempts = maxAttempts;
        this.errorMessage = errorMessage;
        this.claimedBy = claimedBy;
        this.claimedAt = claimedAt;
        this.nextRetryAt = nextRetryAt;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public static DeliveryQueue create(String taskId, String conversationId, String channelName, String message) {
        return new DeliveryQueue(
                null,
                taskId,
                conversationId,
                channelName,
                message,
                Status.pending,
                0,
                3,
                null,
                null,
                null,
                null,
                Instant.now(),
                null);
    }

    public DeliveryQueue withClaimed(String podId) {
        return new DeliveryQueue(
                id,
                taskId,
                conversationId,
                channelName,
                message,
                Status.processing,
                attempts,
                maxAttempts,
                errorMessage,
                podId,
                Instant.now(),
                nextRetryAt,
                createdAt,
                completedAt);
    }

    public DeliveryQueue withDelivered() {
        return new DeliveryQueue(
                id,
                taskId,
                conversationId,
                channelName,
                message,
                Status.delivered,
                attempts + 1,
                maxAttempts,
                null,
                claimedBy,
                claimedAt,
                null,
                createdAt,
                Instant.now());
    }

    public DeliveryQueue withFailed(String error) {
        boolean retriesExhausted = (attempts + 1) >= maxAttempts;
        Instant retry = retriesExhausted ? null : Instant.now().plusSeconds(exponentialBackoff(attempts + 1));
        return new DeliveryQueue(
                id,
                taskId,
                conversationId,
                channelName,
                message,
                retriesExhausted ? Status.failed : Status.pending,
                attempts + 1,
                maxAttempts,
                error,
                null,
                null,
                retry,
                createdAt,
                retriesExhausted ? Instant.now() : null);
    }

    private static long exponentialBackoff(int attempt) {
        // 5s, 25s, 125s
        return (long) Math.pow(5, attempt);
    }

    public boolean isRetriable() {
        return attempts < maxAttempts && status != Status.delivered;
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

    public String getChannelName() {
        return channelName;
    }

    public String getMessage() {
        return message;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    @Override
    public String toString() {
        return "DeliveryQueue{taskId='" + taskId + "', channel='" + channelName + "', status=" + status + ", attempts="
                + attempts + "/" + maxAttempts + "}";
    }
}
