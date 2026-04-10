package ai.javaclaw.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.agent.audit.DeliveryAuditLog;
import ai.javaclaw.agent.audit.DeliveryAuditLogRepository;
import ai.javaclaw.channels.ChannelContextService;
import ai.javaclaw.delivery.DeliveryQueue;
import ai.javaclaw.delivery.DeliveryQueueRepository;
import ai.javaclaw.delivery.DeliveryRecovery;
import ai.javaclaw.tasks.NotifyPolicy;
import ai.javaclaw.tasks.Task;
import ai.javaclaw.tasks.TaskRepository;
import ai.javaclaw.tasks.TaskRuntime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for DeliveryRecovery startup recovery (T54).
 *
 * <p>Verifies that pending deliveries from delivery_queue are processed on startup:
 * <ul>
 *   <li>Pending deliveries with valid routing context → delivered successfully</li>
 *   <li>Pending deliveries without routing context → marked failed</li>
 *   <li>Already delivered/failed deliveries → not reprocessed</li>
 *   <li>Multiple pending deliveries → all processed independently</li>
 *   <li>Delivery audit log entries created for each processed delivery</li>
 * </ul>
 */
class StartupRecoveryIntegrationTest extends IntegrationTestBase {

    @Autowired
    DeliveryRecovery deliveryRecovery;

    @Autowired
    DeliveryQueueRepository deliveryQueueRepository;

    @Autowired
    DeliveryAuditLogRepository deliveryAuditLogRepository;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    ChannelContextService channelContextService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanAll() {
        deliveryAuditLogRepository.deleteAll();
        deliveryQueueRepository.deleteAll();
        taskRepository.deleteAll();
        // Clean up chat memory and conversations created by tests
        jdbcTemplate.update("DELETE FROM spring_ai_chat_memory WHERE conversation_id LIKE 'conv-%'");
        jdbcTemplate.update("DELETE FROM conversation_channel_context WHERE conversation_id LIKE 'conv-%'");
        jdbcTemplate.update("DELETE FROM conversations WHERE id LIKE 'conv-%'");
    }

    private void createConversation(String conversationId) {
        jdbcTemplate.update(
                "INSERT INTO conversations (id, title, created_at, updated_at) VALUES (?, ?, now(), now())"
                        + " ON CONFLICT (id) DO NOTHING",
                conversationId,
                "Test conversation");
    }

    private Task saveTask(String name, String conversationId) {
        Instant now = Instant.now();
        Task task = new Task(
                null,
                name,
                now,
                now,
                Task.Status.completed,
                "test task",
                null,
                null,
                conversationId,
                null,
                NotifyPolicy.done_only,
                TaskRuntime.async,
                300,
                false,
                "user-1",
                null,
                null,
                null);
        return taskRepository.save(task);
    }

    private void setupConversationWithRouting(String conversationId) {
        createConversation(conversationId);
        channelContextService.saveContext(conversationId, "Web Chat Channel", Map.of("conversationId", conversationId));
    }

    private DeliveryQueue savePendingDelivery(
            String taskId, String conversationId, String channelName, String message) {
        DeliveryQueue delivery = DeliveryQueue.create(taskId, conversationId, channelName, message);
        return deliveryQueueRepository.save(delivery);
    }

    @Nested
    @DisplayName("T54: Startup recovery — pending deliveries processed")
    class PendingDeliveryRecovery {

        @Test
        @DisplayName("pending delivery with valid routing context delivered successfully")
        void pendingDeliveryWithRoutingContextDelivered() {
            String convId = "conv-recovery-1";
            setupConversationWithRouting(convId);
            Task task = saveTask("Recovery Task", convId);

            savePendingDelivery(task.getId(), convId, "Web Chat Channel", "Recovered notification");

            deliveryRecovery.recoverPendingDeliveries();

            List<DeliveryQueue> deliveries = deliveryQueueRepository.findByTaskId(task.getId());
            assertThat(deliveries).hasSize(1);
            DeliveryQueue result = deliveries.getFirst();
            assertThat(result.getErrorMessage())
                    .as("delivery error message (should be null for success)")
                    .isNull();
            assertThat(result.getStatus()).isEqualTo(DeliveryQueue.Status.delivered);
            assertThat(result.getClaimedBy()).isNotNull();
            assertThat(result.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("pending delivery creates delivery_audit_log with delivered status")
        void pendingDeliveryCreatesAuditLog() {
            String convId = "conv-audit-recovery-1";
            setupConversationWithRouting(convId);
            Task task = saveTask("Audit Recovery Task", convId);

            savePendingDelivery(task.getId(), convId, "Web Chat Channel", "Audit recovery msg");

            deliveryRecovery.recoverPendingDeliveries();

            List<DeliveryAuditLog> logs = deliveryAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(task.getId());
            assertThat(logs).hasSize(1);
            assertThat(logs.getFirst().status()).isEqualTo("delivered");
            assertThat(logs.getFirst().channelName()).isEqualTo("Web Chat Channel");
            assertThat(logs.getFirst().message()).isEqualTo("Audit recovery msg");
        }

        @Test
        @DisplayName("pending delivery without routing context marked failed")
        void pendingDeliveryWithoutRoutingContextFails() {
            Task task = saveTask("No Route Task", "conv-no-route");

            savePendingDelivery(task.getId(), "conv-no-route", "Web Chat Channel", "Will fail");

            deliveryRecovery.recoverPendingDeliveries();

            List<DeliveryQueue> deliveries = deliveryQueueRepository.findByTaskId(task.getId());
            assertThat(deliveries).hasSize(1);
            DeliveryQueue delivery = deliveries.getFirst();
            assertThat(delivery.getAttempts()).isEqualTo(1);
            assertThat(delivery.getErrorMessage()).contains("No routing context");
        }

        @Test
        @DisplayName("pending delivery without routing context creates failed audit log")
        void failedDeliveryCreatesAuditLog() {
            Task task = saveTask("Failed Audit Task", "conv-no-route-audit");

            savePendingDelivery(task.getId(), "conv-no-route-audit", "Web Chat Channel", "Fail msg");

            deliveryRecovery.recoverPendingDeliveries();

            List<DeliveryAuditLog> logs = deliveryAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(task.getId());
            assertThat(logs).hasSize(1);
            assertThat(logs.getFirst().status()).isEqualTo("failed");
            assertThat(logs.getFirst().errorMessage()).contains("No routing context");
        }
    }

    @Nested
    @DisplayName("T54: Already completed deliveries not reprocessed")
    class CompletedDeliveriesSkipped {

        @Test
        @DisplayName("delivered delivery not picked up by recovery")
        void deliveredDeliveryNotReprocessed() {
            Task task = saveTask("Already Done Task", "conv-done");

            DeliveryQueue pending = DeliveryQueue.create(task.getId(), "conv-done", "Web Chat Channel", "Already sent");
            DeliveryQueue delivered = pending.withClaimed("old-pod").withDelivered();
            deliveryQueueRepository.save(delivered);

            deliveryRecovery.recoverPendingDeliveries();

            List<DeliveryAuditLog> logs = deliveryAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(task.getId());
            assertThat(logs).isEmpty();
        }

        @Test
        @DisplayName("permanently failed delivery not picked up by recovery")
        void permanentlyFailedDeliveryNotReprocessed() {
            Task task = saveTask("Perm Failed Task", "conv-permfail");

            DeliveryQueue pending = DeliveryQueue.create(task.getId(), "conv-permfail", "Web Chat Channel", "Gave up");
            DeliveryQueue fail1 = pending.withFailed("err1");
            DeliveryQueue fail2 = fail1.withFailed("err2");
            DeliveryQueue fail3 = fail2.withFailed("err3");
            deliveryQueueRepository.save(fail3);

            deliveryRecovery.recoverPendingDeliveries();

            List<DeliveryAuditLog> logs = deliveryAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(task.getId());
            assertThat(logs).isEmpty();
        }
    }

    @Nested
    @DisplayName("T54: Multiple pending deliveries processed independently")
    class MultiplePendingDeliveries {

        @Test
        @DisplayName("multiple pending deliveries all processed")
        void multiplePendingDeliveriesProcessed() {
            String convId1 = "conv-multi-r1";
            String convId2 = "conv-multi-r2";
            setupConversationWithRouting(convId1);
            setupConversationWithRouting(convId2);
            Task task1 = saveTask("Multi Task 1", convId1);
            Task task2 = saveTask("Multi Task 2", convId2);

            savePendingDelivery(task1.getId(), convId1, "Web Chat Channel", "Message 1");
            savePendingDelivery(task2.getId(), convId2, "Web Chat Channel", "Message 2");

            deliveryRecovery.recoverPendingDeliveries();

            List<DeliveryQueue> all1 = deliveryQueueRepository.findByTaskId(task1.getId());
            List<DeliveryQueue> all2 = deliveryQueueRepository.findByTaskId(task2.getId());
            assertThat(all1).hasSize(1);
            assertThat(all1.getFirst().getStatus()).isEqualTo(DeliveryQueue.Status.delivered);
            assertThat(all2).hasSize(1);
            assertThat(all2.getFirst().getStatus()).isEqualTo(DeliveryQueue.Status.delivered);
        }

        @Test
        @DisplayName("one failed delivery does not block other deliveries")
        void failedDeliveryDoesNotBlockOthers() {
            String convSuccess = "conv-success-r1";
            setupConversationWithRouting(convSuccess);
            Task task1 = saveTask("Fail Task", "conv-no-route-r1");
            Task task2 = saveTask("Success Task", convSuccess);

            savePendingDelivery(task1.getId(), "conv-no-route-r1", "Web Chat Channel", "Will fail");
            savePendingDelivery(task2.getId(), convSuccess, "Web Chat Channel", "Will succeed");

            deliveryRecovery.recoverPendingDeliveries();

            List<DeliveryQueue> failed = deliveryQueueRepository.findByTaskId(task1.getId());
            List<DeliveryQueue> succeeded = deliveryQueueRepository.findByTaskId(task2.getId());

            assertThat(failed.getFirst().getErrorMessage()).contains("No routing context");
            assertThat(succeeded.getFirst().getStatus()).isEqualTo(DeliveryQueue.Status.delivered);
        }

        @Test
        @DisplayName("recovery sets claimedBy with pod identifier")
        void recoveryClaimesDeliveryWithPodId() {
            String convId = "conv-claim-r1";
            setupConversationWithRouting(convId);
            Task task = saveTask("Claim Task", convId);

            savePendingDelivery(task.getId(), convId, "Web Chat Channel", "Claim me");

            deliveryRecovery.recoverPendingDeliveries();

            List<DeliveryQueue> deliveries = deliveryQueueRepository.findByTaskId(task.getId());
            assertThat(deliveries.getFirst().getClaimedBy()).startsWith("pod-");
        }
    }
}
