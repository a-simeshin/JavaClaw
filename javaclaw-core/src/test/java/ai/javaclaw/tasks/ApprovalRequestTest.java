package ai.javaclaw.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class ApprovalRequestTest {

    private static final Instant TIMEOUT = Instant.now().plus(60, ChronoUnit.SECONDS);

    @Test
    void create_setsPendingStatus() {
        ApprovalRequest req = ApprovalRequest.create("task-1", "conv-1", "Buy ticket for 9500?", TIMEOUT);

        assertThat(req.getId()).isNull();
        assertThat(req.getTaskId()).isEqualTo("task-1");
        assertThat(req.getConversationId()).isEqualTo("conv-1");
        assertThat(req.getQuestion()).isEqualTo("Buy ticket for 9500?");
        assertThat(req.getStatus()).isEqualTo(ApprovalRequest.Status.pending);
        assertThat(req.getResponse()).isNull();
        assertThat(req.getTimeoutAt()).isEqualTo(TIMEOUT);
        assertThat(req.getCreatedAt()).isNotNull();
        assertThat(req.getRespondedAt()).isNull();
    }

    @Test
    void withApproved_setsStatusAndResponse() {
        ApprovalRequest req = ApprovalRequest.create("task-1", "conv-1", "Buy?", TIMEOUT);

        ApprovalRequest approved = req.withApproved("Yes, buy it");

        assertThat(approved.getStatus()).isEqualTo(ApprovalRequest.Status.approved);
        assertThat(approved.getResponse()).isEqualTo("Yes, buy it");
        assertThat(approved.getRespondedAt()).isNotNull();
        assertThat(approved.getTaskId()).isEqualTo("task-1");
        assertThat(approved.getQuestion()).isEqualTo("Buy?");
    }

    @Test
    void withDenied_setsStatusAndResponse() {
        ApprovalRequest req = ApprovalRequest.create("task-2", "conv-1", "Delete file?", TIMEOUT);

        ApprovalRequest denied = req.withDenied("No, keep it");

        assertThat(denied.getStatus()).isEqualTo(ApprovalRequest.Status.denied);
        assertThat(denied.getResponse()).isEqualTo("No, keep it");
        assertThat(denied.getRespondedAt()).isNotNull();
    }

    @Test
    void withTimedOut_setsTimeoutStatus() {
        ApprovalRequest req = ApprovalRequest.create("task-3", "conv-1", "Proceed?", TIMEOUT);

        ApprovalRequest timedOut = req.withTimedOut();

        assertThat(timedOut.getStatus()).isEqualTo(ApprovalRequest.Status.timeout);
        assertThat(timedOut.getResponse()).isNull();
        assertThat(timedOut.getRespondedAt()).isNotNull();
    }

    @Test
    void isPending_returnsTrueOnlyForPending() {
        ApprovalRequest pending = ApprovalRequest.create("task-1", "conv-1", "Q?", TIMEOUT);
        ApprovalRequest approved = pending.withApproved("yes");

        assertThat(pending.isPending()).isTrue();
        assertThat(approved.isPending()).isFalse();
    }

    @Test
    void isExpired_returnsTrueWhenPastTimeout() {
        Instant pastTimeout = Instant.now().minus(10, ChronoUnit.SECONDS);
        ApprovalRequest expired = ApprovalRequest.create("task-1", "conv-1", "Q?", pastTimeout);

        assertThat(expired.isExpired()).isTrue();
    }

    @Test
    void isExpired_returnsFalseWhenNotPending() {
        Instant pastTimeout = Instant.now().minus(10, ChronoUnit.SECONDS);
        ApprovalRequest req = ApprovalRequest.create("task-1", "conv-1", "Q?", pastTimeout);
        ApprovalRequest approved = req.withApproved("yes");

        assertThat(approved.isExpired()).isFalse();
    }

    @Test
    void withApproved_preservesOriginalFields() {
        ApprovalRequest req = ApprovalRequest.create("task-1", "conv-42", "Important question?", TIMEOUT);

        ApprovalRequest approved = req.withApproved("yes");

        assertThat(approved.getTaskId()).isEqualTo("task-1");
        assertThat(approved.getConversationId()).isEqualTo("conv-42");
        assertThat(approved.getQuestion()).isEqualTo("Important question?");
        assertThat(approved.getTimeoutAt()).isEqualTo(TIMEOUT);
        assertThat(approved.getCreatedAt()).isEqualTo(req.getCreatedAt());
    }

    @Test
    void toString_includesKeyInfo() {
        ApprovalRequest req = ApprovalRequest.create("task-99", "conv-1", "Should I proceed?", TIMEOUT);
        String str = req.toString();

        assertThat(str).contains("task-99");
        assertThat(str).contains("pending");
        assertThat(str).contains("Should I proceed?");
    }
}
