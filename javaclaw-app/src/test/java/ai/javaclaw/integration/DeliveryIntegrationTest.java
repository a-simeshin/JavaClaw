package ai.javaclaw.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.javaclaw.agent.audit.DeliveryAuditLog;
import ai.javaclaw.agent.audit.DeliveryAuditLogRepository;
import ai.javaclaw.agent.audit.TaskAuditLog;
import ai.javaclaw.agent.audit.TaskAuditLogRepository;
import ai.javaclaw.delivery.DeliveryService;
import ai.javaclaw.delivery.NotificationTransport;
import ai.javaclaw.tasks.ApprovalRequest;
import ai.javaclaw.tasks.ApprovalRequestRepository;
import ai.javaclaw.tasks.NotifyPolicy;
import ai.javaclaw.tasks.Task;
import ai.javaclaw.tasks.TaskRepository;
import ai.javaclaw.tasks.TaskRuntime;
import ai.javaclaw.tasks.TaskWatchdog;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import reactor.core.Disposable;

/**
 * Integration tests for Delivery pipeline and TaskWatchdog with real PostgreSQL.
 *
 * <p>Covers:
 * <ul>
 *   <li>T55: Web Chat delivery &rarr; SSE event received by test subscriber</li>
 *   <li>T51: Task exceeds timeout &rarr; watchdog marks failed &rarr; error notification delivered</li>
 *   <li>T18/T19/T23/T24: NotifyPolicy filtering with real DeliveryService</li>
 *   <li>T32: Expired approval &rarr; watchdog auto-denies</li>
 * </ul>
 */
class DeliveryIntegrationTest extends IntegrationTestBase {

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    DeliveryAuditLogRepository deliveryAuditLogRepository;

    @Autowired
    TaskAuditLogRepository taskAuditLogRepository;

    @Autowired
    ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    DeliveryService deliveryService;

    @Autowired
    NotificationTransport notificationTransport;

    @Autowired
    TaskWatchdog taskWatchdog;

    @BeforeEach
    void cleanAll() {
        approvalRequestRepository.deleteAll();
        deliveryAuditLogRepository.deleteAll();
        taskAuditLogRepository.deleteAll();
        taskRepository.deleteAll();
    }

    private Task saveTask(
            String name, Task.Status status, NotifyPolicy policy, String conversationId, Integer timeoutSeconds) {
        Instant now = Instant.now();
        Task task = new Task(
                null,
                name,
                now,
                now,
                status,
                "test description",
                null,
                null,
                conversationId,
                null,
                policy,
                TaskRuntime.async,
                timeoutSeconds,
                false,
                "user-1",
                null,
                null,
                null);
        return taskRepository.save(task);
    }

    private Task saveTimedOutTask(String name, int timeoutSeconds) {
        Instant past = Instant.now().minusSeconds(timeoutSeconds + 60);
        Task task = new Task(
                null,
                name,
                past,
                past,
                Task.Status.in_progress,
                "will timeout",
                null,
                null,
                "conv-timeout",
                null,
                NotifyPolicy.done_only,
                TaskRuntime.async,
                timeoutSeconds,
                false,
                "user-1",
                null,
                null,
                null);
        return taskRepository.save(task);
    }

    @Nested
    @DisplayName("T55: Web Chat delivery → SSE event received")
    class WebChatDelivery {

        @Test
        @DisplayName("deliver broadcasts to NotificationTransport and subscriber receives event")
        void deliverBroadcastsToSseSubscriber() {
            Task task = saveTask("SSE Task", Task.Status.completed, NotifyPolicy.done_only, "conv-sse-1", 300);

            List<String> received = new ArrayList<>();
            Disposable subscription =
                    notificationTransport.subscribe("conv-sse-1").subscribe(received::add);

            try {
                deliveryService.deliver(task, "Task completed: SSE Task");

                // InMemoryNotificationTransport delivers synchronously via ApplicationEvent
                assertThat(received).hasSize(1).first().isEqualTo("Task completed: SSE Task");
            } finally {
                subscription.dispose();
            }
        }

        @Test
        @DisplayName("deliver saves delivery_audit_log with delivered status")
        void deliverSavesAuditLog() {
            Task task = saveTask("Audit Task", Task.Status.completed, NotifyPolicy.done_only, "conv-audit-1", 300);

            deliveryService.deliver(task, "Result delivered");

            List<DeliveryAuditLog> logs = deliveryAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(task.getId());
            assertThat(logs).hasSize(1);
            assertThat(logs.getFirst().status()).isEqualTo("delivered");
            assertThat(logs.getFirst().channelName()).isEqualTo("WebChatChannel");
            assertThat(logs.getFirst().message()).isEqualTo("Result delivered");
            assertThat(logs.getFirst().attempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("SSE endpoint returns stream content type for conversation")
        void sseEndpointReturnsStream() throws Exception {
            mockMvc.perform(get("/api/chat/notifications/{conversationId}", "conv-sse-test")
                            .accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("NotifyPolicy filtering integration (T18-T24)")
    class NotifyPolicyFiltering {

        @Test
        @DisplayName("T18: SILENT policy — no delivery, no audit log")
        void silentPolicySkipsDelivery() {
            Task task = saveTask("Silent Task", Task.Status.completed, NotifyPolicy.silent, "conv-silent", 300);

            List<String> received = new ArrayList<>();
            Disposable sub = notificationTransport.subscribe("conv-silent").subscribe(received::add);

            try {
                deliveryService.deliver(task, "Should not arrive");

                assertThat(received).isEmpty();
                assertThat(deliveryAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(task.getId()))
                        .isEmpty();
            } finally {
                sub.dispose();
            }
        }

        @Test
        @DisplayName("T19: DONE_ONLY + completed — delivers")
        void doneOnlyCompletedDelivers() {
            Task task = saveTask("Done Task", Task.Status.completed, NotifyPolicy.done_only, "conv-done", 300);

            deliveryService.deliver(task, "Completed result");

            assertThat(deliveryAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(task.getId()))
                    .hasSize(1);
        }

        @Test
        @DisplayName("T23: ON_SUCCESS + completed — delivers")
        void onSuccessCompletedDelivers() {
            Task task = saveTask("Success Task", Task.Status.completed, NotifyPolicy.on_success, "conv-success", 300);

            deliveryService.deliver(task, "Success!");

            List<DeliveryAuditLog> logs = deliveryAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(task.getId());
            assertThat(logs).hasSize(1);
            assertThat(logs.getFirst().status()).isEqualTo("delivered");
        }

        @Test
        @DisplayName("T24: STATE_CHANGES — delivers on any status")
        void stateChangesDeliversAlways() {
            Task task = saveTask("State Task", Task.Status.in_progress, NotifyPolicy.state_changes, "conv-state", 300);

            deliveryService.deliver(task, "In progress update");

            assertThat(deliveryAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(task.getId()))
                    .hasSize(1);
        }

        @Test
        @DisplayName("ON_ERROR + completed — skips delivery")
        void onErrorCompletedSkips() {
            Task task =
                    saveTask("Error Policy Task", Task.Status.completed, NotifyPolicy.on_error, "conv-error-skip", 300);

            deliveryService.deliver(task, "Should skip");

            assertThat(deliveryAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(task.getId()))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("T51: TaskWatchdog timeout → failed + notification")
    class WatchdogTimeout {

        @Test
        @DisplayName("timed out task marked failed with 'Timed out' feedback")
        void timedOutTaskMarkedFailed() {
            Task task = saveTimedOutTask("Stuck Task", 10);

            taskWatchdog.checkTimeouts();

            Task updated = taskRepository.findById(task.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(Task.Status.failed);
            assertThat(updated.getFeedback()).isEqualTo("Timed out");
        }

        @Test
        @DisplayName("timed out task generates delivery_audit_log entry")
        void timedOutTaskCreatesDeliveryAudit() {
            Task task = saveTimedOutTask("Timeout Audit Task", 5);

            taskWatchdog.checkTimeouts();

            List<DeliveryAuditLog> logs = deliveryAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(task.getId());
            assertThat(logs).hasSize(1);
            assertThat(logs.getFirst().status()).isEqualTo("delivered");
            assertThat(logs.getFirst().message()).contains("timed out");
        }

        @Test
        @DisplayName("timed out task generates task_audit_log timeout event")
        void timedOutTaskCreatesAuditEvent() {
            Task task = saveTimedOutTask("Audit Event Task", 5);

            taskWatchdog.checkTimeouts();

            List<TaskAuditLog> logs = taskAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(task.getId());
            assertThat(logs).isNotEmpty();
            assertThat(logs.stream().map(TaskAuditLog::eventType)).contains("timeout");
        }

        @Test
        @DisplayName("timed out task sends SSE notification to subscriber")
        void timedOutTaskSendsNotification() {
            Task task = saveTimedOutTask("SSE Timeout Task", 5);

            List<String> received = new ArrayList<>();
            Disposable sub = notificationTransport.subscribe("conv-timeout").subscribe(received::add);

            try {
                taskWatchdog.checkTimeouts();

                assertThat(received).hasSize(1).first().asString().contains("timed out");
            } finally {
                sub.dispose();
            }
        }

        @Test
        @DisplayName("task within timeout not touched by watchdog")
        void taskWithinTimeoutNotTouched() {
            Task task = saveTask("Fresh Task", Task.Status.in_progress, NotifyPolicy.done_only, "conv-fresh", 300);

            taskWatchdog.checkTimeouts();

            Task updated = taskRepository.findById(task.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(Task.Status.in_progress);
        }

        @Test
        @DisplayName("task without timeout never timed out")
        void taskWithoutTimeoutNeverTimedOut() {
            Instant past = Instant.now().minusSeconds(999);
            Task task = new Task(
                    null,
                    "No Timeout Task",
                    past,
                    past,
                    Task.Status.in_progress,
                    "no timeout",
                    null,
                    null,
                    "conv-notimeout",
                    null,
                    NotifyPolicy.done_only,
                    TaskRuntime.async,
                    null,
                    false,
                    "user-1",
                    null,
                    null,
                    null);
            Task saved = taskRepository.save(task);

            taskWatchdog.checkTimeouts();

            Task updated = taskRepository.findById(saved.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(Task.Status.in_progress);
        }
    }

    @Nested
    @DisplayName("T32: Watchdog expired approval → auto-deny")
    class WatchdogApprovalTimeout {

        @Test
        @DisplayName("expired approval marked as timeout")
        void expiredApprovalTimedOut() {
            Task task = saveTask(
                    "Approval Task", Task.Status.awaiting_human_input, NotifyPolicy.done_only, "conv-approval", 300);

            // Create approval with timeout already in the past
            Instant pastTimeout = Instant.now().minusSeconds(60);
            ApprovalRequest approval =
                    ApprovalRequest.create(task.getId(), "conv-approval", "Buy ticket?", pastTimeout);
            approvalRequestRepository.save(approval);

            taskWatchdog.checkTimeouts();

            List<ApprovalRequest> remaining = approvalRequestRepository.findByConversationIdAndStatus(
                    "conv-approval", ApprovalRequest.Status.pending);
            assertThat(remaining).isEmpty();
        }

        @Test
        @DisplayName("non-expired approval not touched")
        void nonExpiredApprovalNotTouched() {
            Task task = saveTask(
                    "Future Approval Task",
                    Task.Status.awaiting_human_input,
                    NotifyPolicy.done_only,
                    "conv-future",
                    300);

            // Create approval with timeout far in the future
            Instant futureTimeout = Instant.now().plusSeconds(3600);
            ApprovalRequest approval = ApprovalRequest.create(task.getId(), "conv-future", "Proceed?", futureTimeout);
            approvalRequestRepository.save(approval);

            taskWatchdog.checkTimeouts();

            List<ApprovalRequest> pending = approvalRequestRepository.findByConversationIdAndStatus(
                    "conv-future", ApprovalRequest.Status.pending);
            assertThat(pending).hasSize(1);
        }
    }
}
