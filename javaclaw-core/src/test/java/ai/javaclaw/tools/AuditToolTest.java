package ai.javaclaw.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ai.javaclaw.agent.audit.*;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AuditToolTest {

    private TaskAuditLogRepository taskAuditLogRepository;
    private ChatAuditLogRepository chatAuditLogRepository;
    private DeliveryAuditLogRepository deliveryAuditLogRepository;
    private AuditTool auditTool;

    @BeforeEach
    void setUp() {
        taskAuditLogRepository = mock(TaskAuditLogRepository.class);
        chatAuditLogRepository = mock(ChatAuditLogRepository.class);
        deliveryAuditLogRepository = mock(DeliveryAuditLogRepository.class);
        auditTool = new AuditTool(taskAuditLogRepository, chatAuditLogRepository, deliveryAuditLogRepository);
    }

    @Nested
    class GetTaskAudit {

        @Test
        void returnsFullExecutionHistory() {
            // T41: getTaskAudit → returns full execution history
            String taskId = "task-123";
            Instant now = Instant.now();
            List<TaskAuditLog> logs = List.of(
                    new TaskAuditLog(
                            1L, taskId, null, "created", now, null, null, null, null, null, null, null, null, null,
                            null, null, null, null),
                    new TaskAuditLog(
                            2L,
                            taskId,
                            "exec-1",
                            "started",
                            now.plusSeconds(1),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            0L,
                            null),
                    new TaskAuditLog(
                            3L,
                            taskId,
                            "exec-1",
                            "tool_call",
                            now.plusSeconds(2),
                            null,
                            null,
                            "weatherTool",
                            "{\"city\":\"Moscow\"}",
                            "Sunny, 20°C",
                            150L,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null),
                    new TaskAuditLog(
                            4L,
                            taskId,
                            "exec-1",
                            "completed",
                            now.plusSeconds(5),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            "Weather is sunny",
                            null,
                            null,
                            null,
                            5000L,
                            null));
            when(taskAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(taskId)).thenReturn(logs);

            String result = auditTool.getTaskAudit(taskId);

            assertThat(result).contains("Task Audit Trail for 'task-123'");
            assertThat(result).contains("4 events");
            assertThat(result).contains("CREATED");
            assertThat(result).contains("STARTED");
            assertThat(result).contains("TOOL_CALL");
            assertThat(result).contains("weatherTool");
            assertThat(result).contains("150ms");
            assertThat(result).contains("{\"city\":\"Moscow\"}");
            assertThat(result).contains("Sunny, 20°C");
            assertThat(result).contains("COMPLETED");
            assertThat(result).contains("5000ms");
            assertThat(result).contains("Weather is sunny");
        }

        @Test
        void returnsErrorDetailsWhenTaskFailed() {
            String taskId = "task-fail";
            Instant now = Instant.now();
            List<TaskAuditLog> logs = List.of(new TaskAuditLog(
                    1L,
                    taskId,
                    "exec-1",
                    "failed",
                    now,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "Connection timeout",
                    "java.net.SocketTimeoutException\n  at ...",
                    3000L,
                    null));
            when(taskAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(taskId)).thenReturn(logs);

            String result = auditTool.getTaskAudit(taskId);

            assertThat(result).contains("FAILED");
            assertThat(result).contains("Error: Connection timeout");
            assertThat(result).contains("Stacktrace: java.net.SocketTimeoutException");
            assertThat(result).contains("3000ms");
        }

        @Test
        void returnsLlmRequestResponseDetails() {
            String taskId = "task-llm";
            Instant now = Instant.now();
            List<TaskAuditLog> logs = List.of(new TaskAuditLog(
                    1L,
                    taskId,
                    "exec-1",
                    "started",
                    now,
                    "You are an assistant",
                    "Get weather for Moscow",
                    null,
                    null,
                    null,
                    null,
                    "full request json",
                    "full response json",
                    "{\"prompt_tokens\":100,\"completion_tokens\":50}",
                    null,
                    null,
                    2000L,
                    null));
            when(taskAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(taskId)).thenReturn(logs);

            String result = auditTool.getTaskAudit(taskId);

            assertThat(result).contains("System prompt: You are an assistant");
            assertThat(result).contains("User prompt: Get weather for Moscow");
            assertThat(result).contains("LLM request: full request json");
            assertThat(result).contains("LLM response: full response json");
            assertThat(result).contains("Token usage: {\"prompt_tokens\":100,\"completion_tokens\":50}");
        }

        @Test
        void returnsNotFoundWhenNoLogs() {
            when(taskAuditLogRepository.findByTaskIdOrderByCreatedAtAsc("nonexistent"))
                    .thenReturn(Collections.emptyList());

            String result = auditTool.getTaskAudit("nonexistent");

            assertThat(result).contains("No audit records found for task 'nonexistent'");
        }

        @Test
        void returnsErrorOnRepositoryException() {
            when(taskAuditLogRepository.findByTaskIdOrderByCreatedAtAsc("bad"))
                    .thenThrow(new RuntimeException("DB connection failed"));

            String result = auditTool.getTaskAudit("bad");

            assertThat(result).contains("Error: Could not retrieve task audit");
            assertThat(result).contains("DB connection failed");
        }
    }

    @Nested
    class GetChatAudit {

        @Test
        void returnsChatRequestsWithToolDetails() {
            // T42: getChatAudit → returns chat requests with tool details
            String convId = "conv-456";
            Instant now = Instant.now();
            List<ChatAuditLog> logs = List.of(
                    new ChatAuditLog(
                            1L,
                            convId,
                            now,
                            "stream",
                            "You are helpful",
                            "[]",
                            "What's the weather?",
                            "weatherTool",
                            "It's sunny in Moscow",
                            null,
                            null,
                            1500L,
                            "user-1",
                            "[{\"name\":\"weatherTool\",\"args\":{\"city\":\"Moscow\"},\"result\":\"Sunny\",\"duration_ms\":200}]",
                            "{\"prompt_tokens\":50,\"completion_tokens\":30}"),
                    new ChatAuditLog(
                            2L,
                            convId,
                            now.minusSeconds(60),
                            "call",
                            null,
                            null,
                            "Hello",
                            null,
                            "Hi there!",
                            null,
                            null,
                            500L,
                            "user-1",
                            null,
                            null));
            when(chatAuditLogRepository.findByConversationIdOrderByCreatedAtDesc(convId))
                    .thenReturn(logs);

            String result = auditTool.getChatAudit(convId, 10);

            assertThat(result).contains("Chat Audit for conversation 'conv-456'");
            assertThat(result).contains("showing 2 of 2");
            assertThat(result).contains("STREAM");
            assertThat(result).contains("1500ms");
            assertThat(result).contains("User: What's the weather?");
            assertThat(result).contains("Response: It's sunny in Moscow");
            assertThat(result).contains("Tools used: weatherTool");
            assertThat(result).contains("Tool calls detail:");
            assertThat(result).contains("Token usage:");
            assertThat(result).contains("CALL");
            assertThat(result).contains("User: Hello");
        }

        @Test
        void respectsLimitParameter() {
            String convId = "conv-many";
            Instant now = Instant.now();
            List<ChatAuditLog> logs = List.of(
                    new ChatAuditLog(
                            1L, convId, now, "stream", null, null, "Q1", null, "A1", null, null, 100L, null, null,
                            null),
                    new ChatAuditLog(
                            2L,
                            convId,
                            now.minusSeconds(10),
                            "stream",
                            null,
                            null,
                            "Q2",
                            null,
                            "A2",
                            null,
                            null,
                            200L,
                            null,
                            null,
                            null),
                    new ChatAuditLog(
                            3L,
                            convId,
                            now.minusSeconds(20),
                            "stream",
                            null,
                            null,
                            "Q3",
                            null,
                            "A3",
                            null,
                            null,
                            300L,
                            null,
                            null,
                            null));
            when(chatAuditLogRepository.findByConversationIdOrderByCreatedAtDesc(convId))
                    .thenReturn(logs);

            String result = auditTool.getChatAudit(convId, 2);

            assertThat(result).contains("showing 2 of 3");
            assertThat(result).contains("User: Q1");
            assertThat(result).contains("User: Q2");
            assertThat(result).doesNotContain("User: Q3");
        }

        @Test
        void returnsNotFoundWhenNoLogs() {
            when(chatAuditLogRepository.findByConversationIdOrderByCreatedAtDesc("empty"))
                    .thenReturn(Collections.emptyList());

            String result = auditTool.getChatAudit("empty", 10);

            assertThat(result).contains("No chat audit records found for conversation 'empty'");
        }

        @Test
        void showsErrorInChatAudit() {
            String convId = "conv-err";
            List<ChatAuditLog> logs = List.of(new ChatAuditLog(
                    1L,
                    convId,
                    Instant.now(),
                    "stream",
                    null,
                    null,
                    "fail query",
                    null,
                    null,
                    "Model overloaded",
                    "stack...",
                    0L,
                    null,
                    null,
                    null));
            when(chatAuditLogRepository.findByConversationIdOrderByCreatedAtDesc(convId))
                    .thenReturn(logs);

            String result = auditTool.getChatAudit(convId, 5);

            assertThat(result).contains("Error: Model overloaded");
        }
    }

    @Nested
    class GetDeliveryAudit {

        @Test
        void returnsDeliveryAttempts() {
            // T43: getDeliveryAudit → returns delivery attempts
            String taskId = "task-789";
            Instant now = Instant.now();
            List<DeliveryAuditLog> logs = List.of(
                    DeliveryAuditLog.delivered(
                            taskId, "conv-1", "WebChatChannel", "Task completed: weather report", 1, 50L),
                    DeliveryAuditLog.failed(
                            taskId,
                            "conv-1",
                            "TelegramChannel",
                            "Task completed: weather report",
                            3,
                            "Telegram API timeout",
                            5000L));
            when(deliveryAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(taskId))
                    .thenReturn(logs);

            String result = auditTool.getDeliveryAudit(taskId);

            assertThat(result).contains("Delivery Audit for task 'task-789'");
            assertThat(result).contains("2 deliveries");
            assertThat(result).contains("DELIVERED via WebChatChannel");
            assertThat(result).contains("Attempts: 1");
            assertThat(result).contains("FAILED via TelegramChannel");
            assertThat(result).contains("Attempts: 3");
            assertThat(result).contains("Error: Telegram API timeout");
            assertThat(result).contains("5000ms");
        }

        @Test
        void returnsNotFoundWhenNoDeliveries() {
            when(deliveryAuditLogRepository.findByTaskIdOrderByCreatedAtAsc("no-delivery"))
                    .thenReturn(Collections.emptyList());

            String result = auditTool.getDeliveryAudit("no-delivery");

            assertThat(result).contains("No delivery records found for task 'no-delivery'");
        }

        @Test
        void returnsErrorOnRepositoryException() {
            when(deliveryAuditLogRepository.findByTaskIdOrderByCreatedAtAsc("bad"))
                    .thenThrow(new RuntimeException("Connection refused"));

            String result = auditTool.getDeliveryAudit("bad");

            assertThat(result).contains("Error: Could not retrieve delivery audit");
            assertThat(result).contains("Connection refused");
        }
    }

    @Test
    void truncatesLongTextInOutput() {
        String taskId = "task-long";
        String longText = "x".repeat(1000);
        List<TaskAuditLog> logs = List.of(new TaskAuditLog(
                1L,
                taskId,
                null,
                "failed",
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                longText,
                longText,
                null,
                null));
        when(taskAuditLogRepository.findByTaskIdOrderByCreatedAtAsc(taskId)).thenReturn(logs);

        String result = auditTool.getTaskAudit(taskId);

        // Error message truncated to 200 chars + "..."
        assertThat(result).doesNotContain("x".repeat(1000));
        assertThat(result).contains("...");
    }
}
