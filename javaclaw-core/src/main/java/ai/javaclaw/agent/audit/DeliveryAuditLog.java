package ai.javaclaw.agent.audit;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Immutable audit record for notification delivery attempts. Tracks every attempt
 * to deliver a task result to a user through a specific channel, including retries
 * and failures.
 *
 * <p>Users can query this table (via AuditTool) to understand delivery history:
 * which channel was used, how many attempts were made, whether delivery succeeded,
 * and what errors occurred.
 *
 * <p>Uses auto-generated BIGINT identity (not UUID) since audit records are
 * append-only and never referenced by other entities.
 */
@Table("delivery_audit_log")
public record DeliveryAuditLog(
        @Id Long id,
        @Column("task_id") String taskId,
        @Column("conversation_id") String conversationId,
        @Column("channel_name") String channelName,
        @Column("message") String message,
        @Column("status") String status,
        @Column("attempts") int attempts,
        @Column("error_message") String errorMessage,
        @Column("duration_ms") Long durationMs,
        @Column("created_at") Instant createdAt) {

    public static final String STATUS_DELIVERED = "delivered";
    public static final String STATUS_FAILED = "failed";

    /** Creates a successful delivery audit record. */
    public static DeliveryAuditLog delivered(
            String taskId, String conversationId, String channelName, String message, int attempts, Long durationMs) {
        return new DeliveryAuditLog(
                null,
                taskId,
                conversationId,
                channelName,
                message,
                STATUS_DELIVERED,
                attempts,
                null,
                durationMs,
                Instant.now());
    }

    /** Creates a failed delivery audit record. */
    public static DeliveryAuditLog failed(
            String taskId,
            String conversationId,
            String channelName,
            String message,
            int attempts,
            String errorMessage,
            Long durationMs) {
        return new DeliveryAuditLog(
                null,
                taskId,
                conversationId,
                channelName,
                message,
                STATUS_FAILED,
                attempts,
                errorMessage,
                durationMs,
                Instant.now());
    }
}
