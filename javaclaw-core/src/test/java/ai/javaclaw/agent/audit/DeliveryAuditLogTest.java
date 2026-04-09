package ai.javaclaw.agent.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeliveryAuditLogTest {

    @Test
    void delivered_setsAllFieldsCorrectly() {
        DeliveryAuditLog log = DeliveryAuditLog.delivered("task-1", "conv-1", "telegram", "Task completed!", 2, 150L);

        assertThat(log.id()).isNull();
        assertThat(log.taskId()).isEqualTo("task-1");
        assertThat(log.conversationId()).isEqualTo("conv-1");
        assertThat(log.channelName()).isEqualTo("telegram");
        assertThat(log.message()).isEqualTo("Task completed!");
        assertThat(log.status()).isEqualTo(DeliveryAuditLog.STATUS_DELIVERED);
        assertThat(log.attempts()).isEqualTo(2);
        assertThat(log.errorMessage()).isNull();
        assertThat(log.durationMs()).isEqualTo(150L);
        assertThat(log.createdAt()).isNotNull();
    }

    @Test
    void failed_setsErrorMessage() {
        DeliveryAuditLog log = DeliveryAuditLog.failed(
                "task-2", "conv-2", "discord", "Error notification", 3, "Connection refused", 5000L);

        assertThat(log.taskId()).isEqualTo("task-2");
        assertThat(log.conversationId()).isEqualTo("conv-2");
        assertThat(log.channelName()).isEqualTo("discord");
        assertThat(log.status()).isEqualTo(DeliveryAuditLog.STATUS_FAILED);
        assertThat(log.attempts()).isEqualTo(3);
        assertThat(log.errorMessage()).isEqualTo("Connection refused");
        assertThat(log.durationMs()).isEqualTo(5000L);
    }

    @Test
    void statusConstants_haveCorrectValues() {
        assertThat(DeliveryAuditLog.STATUS_DELIVERED).isEqualTo("delivered");
        assertThat(DeliveryAuditLog.STATUS_FAILED).isEqualTo("failed");
    }

    @Test
    void delivered_withNullTaskId_isAllowed() {
        DeliveryAuditLog log = DeliveryAuditLog.delivered(null, "conv-3", "web_chat", "Notification", 1, 50L);

        assertThat(log.taskId()).isNull();
        assertThat(log.conversationId()).isEqualTo("conv-3");
        assertThat(log.status()).isEqualTo("delivered");
    }

    @Test
    void failed_preservesAllFields() {
        DeliveryAuditLog log =
                DeliveryAuditLog.failed("task-4", "conv-4", "telegram", "Retry message", 1, "Timeout", 3000L);

        assertThat(log.taskId()).isEqualTo("task-4");
        assertThat(log.conversationId()).isEqualTo("conv-4");
        assertThat(log.channelName()).isEqualTo("telegram");
        assertThat(log.message()).isEqualTo("Retry message");
        assertThat(log.status()).isEqualTo("failed");
        assertThat(log.attempts()).isEqualTo(1);
        assertThat(log.errorMessage()).isEqualTo("Timeout");
        assertThat(log.durationMs()).isEqualTo(3000L);
        assertThat(log.createdAt()).isNotNull();
    }
}
