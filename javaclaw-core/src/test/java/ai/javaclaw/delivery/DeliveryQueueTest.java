package ai.javaclaw.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeliveryQueueTest {

    @Test
    void create_setsPendingStatus() {
        DeliveryQueue dq = DeliveryQueue.create("task-1", "conv-1", "telegram", "Hello!");

        assertThat(dq.getId()).isNull();
        assertThat(dq.getTaskId()).isEqualTo("task-1");
        assertThat(dq.getConversationId()).isEqualTo("conv-1");
        assertThat(dq.getChannelName()).isEqualTo("telegram");
        assertThat(dq.getMessage()).isEqualTo("Hello!");
        assertThat(dq.getStatus()).isEqualTo(DeliveryQueue.Status.pending);
        assertThat(dq.getAttempts()).isZero();
        assertThat(dq.getMaxAttempts()).isEqualTo(3);
        assertThat(dq.getErrorMessage()).isNull();
        assertThat(dq.getClaimedBy()).isNull();
        assertThat(dq.getClaimedAt()).isNull();
        assertThat(dq.getNextRetryAt()).isNull();
        assertThat(dq.getCreatedAt()).isNotNull();
        assertThat(dq.getCompletedAt()).isNull();
    }

    @Test
    void withClaimed_setsProcessingStatusAndPodId() {
        DeliveryQueue dq = DeliveryQueue.create("task-1", "conv-1", "telegram", "Hello!");

        DeliveryQueue claimed = dq.withClaimed("pod-abc");

        assertThat(claimed.getStatus()).isEqualTo(DeliveryQueue.Status.processing);
        assertThat(claimed.getClaimedBy()).isEqualTo("pod-abc");
        assertThat(claimed.getClaimedAt()).isNotNull();
        assertThat(claimed.getTaskId()).isEqualTo("task-1");
        assertThat(claimed.getMessage()).isEqualTo("Hello!");
    }

    @Test
    void withDelivered_setsDeliveredStatusAndIncrements() {
        DeliveryQueue dq =
                DeliveryQueue.create("task-1", "conv-1", "discord", "msg").withClaimed("pod-1");

        DeliveryQueue delivered = dq.withDelivered();

        assertThat(delivered.getStatus()).isEqualTo(DeliveryQueue.Status.delivered);
        assertThat(delivered.getAttempts()).isEqualTo(1);
        assertThat(delivered.getCompletedAt()).isNotNull();
        assertThat(delivered.getErrorMessage()).isNull();
        assertThat(delivered.getNextRetryAt()).isNull();
    }

    @Test
    void withFailed_firstAttempt_setsRetryPending() {
        DeliveryQueue dq =
                DeliveryQueue.create("task-1", "conv-1", "telegram", "msg").withClaimed("pod-1");

        DeliveryQueue failed = dq.withFailed("Connection refused");

        assertThat(failed.getStatus()).isEqualTo(DeliveryQueue.Status.pending);
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getErrorMessage()).isEqualTo("Connection refused");
        assertThat(failed.getNextRetryAt()).isNotNull();
        assertThat(failed.getClaimedBy()).isNull();
        assertThat(failed.getClaimedAt()).isNull();
        assertThat(failed.getCompletedAt()).isNull();
    }

    @Test
    void withFailed_maxAttemptsReached_setsFailedStatus() {
        DeliveryQueue dq = DeliveryQueue.create("task-1", "conv-1", "telegram", "msg");
        // Simulate 2 previous failed attempts
        DeliveryQueue after2 = dq.withFailed("err1").withFailed("err2");

        DeliveryQueue finalFail = after2.withFailed("err3");

        assertThat(finalFail.getStatus()).isEqualTo(DeliveryQueue.Status.failed);
        assertThat(finalFail.getAttempts()).isEqualTo(3);
        assertThat(finalFail.getCompletedAt()).isNotNull();
        assertThat(finalFail.getNextRetryAt()).isNull();
    }

    @Test
    void isRetriable_trueWhenUnderMaxAttempts() {
        DeliveryQueue dq = DeliveryQueue.create("task-1", "conv-1", "telegram", "msg");

        assertThat(dq.isRetriable()).isTrue();
    }

    @Test
    void isRetriable_falseWhenDelivered() {
        DeliveryQueue delivered = DeliveryQueue.create("task-1", "conv-1", "telegram", "msg")
                .withClaimed("pod-1")
                .withDelivered();

        assertThat(delivered.isRetriable()).isFalse();
    }

    @Test
    void isRetriable_falseWhenMaxAttemptsExhausted() {
        DeliveryQueue exhausted = DeliveryQueue.create("task-1", "conv-1", "telegram", "msg")
                .withFailed("e1")
                .withFailed("e2")
                .withFailed("e3");

        assertThat(exhausted.isRetriable()).isFalse();
    }

    @Test
    void withDelivered_preservesOriginalFields() {
        DeliveryQueue dq = DeliveryQueue.create("task-42", "conv-99", "discord", "Important notification")
                .withClaimed("pod-xyz");

        DeliveryQueue delivered = dq.withDelivered();

        assertThat(delivered.getTaskId()).isEqualTo("task-42");
        assertThat(delivered.getConversationId()).isEqualTo("conv-99");
        assertThat(delivered.getChannelName()).isEqualTo("discord");
        assertThat(delivered.getMessage()).isEqualTo("Important notification");
        assertThat(delivered.getCreatedAt()).isEqualTo(dq.getCreatedAt());
    }

    @Test
    void toString_includesKeyInfo() {
        DeliveryQueue dq = DeliveryQueue.create("task-7", "conv-1", "telegram", "msg");
        String str = dq.toString();

        assertThat(str).contains("task-7");
        assertThat(str).contains("telegram");
        assertThat(str).contains("pending");
        assertThat(str).contains("0/3");
    }
}
