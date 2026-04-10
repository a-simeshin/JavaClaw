package ai.javaclaw.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.javaclaw.agent.audit.DeliveryAuditLog;
import ai.javaclaw.agent.audit.DeliveryAuditLogRepository;
import ai.javaclaw.agent.audit.TaskAuditLog;
import ai.javaclaw.agent.audit.TaskAuditLogRepository;
import ai.javaclaw.tasks.NotifyPolicy;
import ai.javaclaw.tasks.Task;
import ai.javaclaw.tasks.TaskExecution;
import ai.javaclaw.tasks.TaskExecutionRepository;
import ai.javaclaw.tasks.TaskRepository;
import ai.javaclaw.tasks.TaskRuntime;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Integration tests for Task Audit REST API endpoints
 * ({@code /api/tasks/{taskId}/audit}, {@code /api/tasks/{taskId}/executions},
 * {@code /api/tasks/{taskId}/deliveries}).
 *
 * <p>Covers integration test scenarios T58 (full task lifecycle audit trail)
 * and T60 (audit query via API) with real PostgreSQL via Testcontainers.
 */
class TaskAuditApiIntegrationTest extends IntegrationTestBase {

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    TaskAuditLogRepository taskAuditLogRepository;

    @Autowired
    TaskExecutionRepository taskExecutionRepository;

    @Autowired
    DeliveryAuditLogRepository deliveryAuditLogRepository;

    @BeforeEach
    void cleanAll() {
        deliveryAuditLogRepository.deleteAll();
        taskAuditLogRepository.deleteAll();
        taskExecutionRepository.deleteAll();
        taskRepository.deleteAll();
    }

    private Task saveTask(String name, Task.Status status) {
        Task task = new Task(
                null,
                name,
                Instant.now(),
                Instant.now(),
                status,
                "test description",
                null,
                null,
                "conv-1",
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

    @Nested
    class AuditEndpoint {

        @Test
        void emptyAuditLog_returnsEmptyArray() throws Exception {
            Task task = saveTask("No Audit", Task.Status.completed);

            mockMvc.perform(get("/api/tasks/{taskId}/audit", task.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void returnsAuditEventsOrderedByCreatedAt() throws Exception {
            Task task = saveTask("Audited Task", Task.Status.completed);

            // Insert audit events in lifecycle order
            taskAuditLogRepository.save(TaskAuditLog.taskEvent(task.getId(), "exec-1", TaskAuditLog.EVENT_CREATED));
            taskAuditLogRepository.save(TaskAuditLog.taskEvent(task.getId(), "exec-1", TaskAuditLog.EVENT_STARTED));
            taskAuditLogRepository.save(TaskAuditLog.toolCallEvent(
                    task.getId(), "exec-1", "getWeather", "{\"city\":\"Moscow\"}", "Sunny, 22C", 150L));
            taskAuditLogRepository.save(TaskAuditLog.taskEvent(task.getId(), "exec-1", TaskAuditLog.EVENT_COMPLETED));

            mockMvc.perform(get("/api/tasks/{taskId}/audit", task.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4))
                    .andExpect(jsonPath("$[0].eventType").value("created"))
                    .andExpect(jsonPath("$[1].eventType").value("started"))
                    .andExpect(jsonPath("$[2].eventType").value("tool_call"))
                    .andExpect(jsonPath("$[3].eventType").value("completed"));
        }

        @Test
        void toolCallEventContainsToolDetails() throws Exception {
            Task task = saveTask("Tool Task", Task.Status.completed);

            taskAuditLogRepository.save(TaskAuditLog.toolCallEvent(
                    task.getId(), "exec-1", "searchWeb", "{\"query\":\"weather\"}", "Results found", 250L));

            mockMvc.perform(get("/api/tasks/{taskId}/audit", task.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].toolName").value("searchWeb"))
                    .andExpect(jsonPath("$[0].toolArgs").value("{\"query\":\"weather\"}"))
                    .andExpect(jsonPath("$[0].toolResult").value("Results found"))
                    .andExpect(jsonPath("$[0].toolDurationMs").value(250));
        }

        @Test
        void errorEventContainsErrorDetails() throws Exception {
            Task task = saveTask("Failed Task", Task.Status.failed);

            taskAuditLogRepository.save(TaskAuditLog.errorEvent(
                    task.getId(), "exec-1", "Connection refused", "java.net.ConnectException...", 5000L));

            mockMvc.perform(get("/api/tasks/{taskId}/audit", task.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].eventType").value("failed"))
                    .andExpect(jsonPath("$[0].errorMessage").value("Connection refused"))
                    .andExpect(jsonPath("$[0].errorTrace").value("java.net.ConnectException..."))
                    .andExpect(jsonPath("$[0].durationMs").value(5000));
        }

        @Test
        void llmEventContainsPromptsAndResponse() throws Exception {
            Task task = saveTask("LLM Task", Task.Status.completed);

            taskAuditLogRepository.save(TaskAuditLog.llmEvent(
                    task.getId(),
                    "exec-1",
                    "You are a helpful assistant",
                    "What is the weather?",
                    "full-llm-request-json",
                    "The weather is sunny",
                    "{\"prompt_tokens\":50,\"completion_tokens\":10}",
                    1200L));

            mockMvc.perform(get("/api/tasks/{taskId}/audit", task.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].systemPrompt").value("You are a helpful assistant"))
                    .andExpect(jsonPath("$[0].userPrompt").value("What is the weather?"))
                    .andExpect(jsonPath("$[0].llmRequest").value("full-llm-request-json"))
                    .andExpect(jsonPath("$[0].llmResponse").value("The weather is sunny"))
                    .andExpect(jsonPath("$[0].tokenUsage").value("{\"prompt_tokens\":50,\"completion_tokens\":10}"))
                    .andExpect(jsonPath("$[0].durationMs").value(1200));
        }

        @Test
        void auditIsolatedByTaskId() throws Exception {
            Task taskA = saveTask("Task A", Task.Status.completed);
            Task taskB = saveTask("Task B", Task.Status.completed);

            taskAuditLogRepository.save(TaskAuditLog.taskEvent(taskA.getId(), "exec-a", TaskAuditLog.EVENT_CREATED));
            taskAuditLogRepository.save(TaskAuditLog.taskEvent(taskB.getId(), "exec-b", TaskAuditLog.EVENT_CREATED));
            taskAuditLogRepository.save(TaskAuditLog.taskEvent(taskB.getId(), "exec-b", TaskAuditLog.EVENT_COMPLETED));

            mockMvc.perform(get("/api/tasks/{taskId}/audit", taskA.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].eventType").value("created"));

            mockMvc.perform(get("/api/tasks/{taskId}/audit", taskB.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    @Nested
    class ExecutionsEndpoint {

        @Test
        void emptyExecutions_returnsEmptyArray() throws Exception {
            Task task = saveTask("No Executions", Task.Status.completed);

            mockMvc.perform(get("/api/tasks/{taskId}/executions", task.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void returnsExecutionsOrderedByNumberDesc() throws Exception {
            Task task = saveTask("Multi-Exec Task", Task.Status.completed);

            TaskExecution exec1 = TaskExecution.start(task.getId(), 1, "system prompt", "user prompt 1");
            exec1 = exec1.withCompleted("Response 1", null, null);
            taskExecutionRepository.save(exec1);

            TaskExecution exec2 = TaskExecution.start(task.getId(), 2, "system prompt", "user prompt 2");
            exec2 = exec2.withCompleted("Response 2", null, null);
            taskExecutionRepository.save(exec2);

            mockMvc.perform(get("/api/tasks/{taskId}/executions", task.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    // Ordered DESC: execution 2 first, then 1
                    .andExpect(jsonPath("$[0].executionNumber").value(2))
                    .andExpect(jsonPath("$[0].userPrompt").value("user prompt 2"))
                    .andExpect(jsonPath("$[0].llmResponse").value("Response 2"))
                    .andExpect(jsonPath("$[1].executionNumber").value(1))
                    .andExpect(jsonPath("$[1].userPrompt").value("user prompt 1"));
        }

        @Test
        void completedExecutionContainsFullDetails() throws Exception {
            Task task = saveTask("Detailed Task", Task.Status.completed);

            TaskExecution exec = TaskExecution.start(task.getId(), 1, "You are helpful", "Tell a joke");
            exec = exec.withCompleted(
                    "Why did the chicken cross the road?",
                    "[{\"name\":\"getJoke\",\"result\":\"joke text\"}]",
                    "{\"prompt_tokens\":30,\"completion_tokens\":15}");
            taskExecutionRepository.save(exec);

            mockMvc.perform(get("/api/tasks/{taskId}/executions", task.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("completed"))
                    .andExpect(jsonPath("$[0].systemPrompt").value("You are helpful"))
                    .andExpect(jsonPath("$[0].userPrompt").value("Tell a joke"))
                    .andExpect(jsonPath("$[0].llmResponse").value("Why did the chicken cross the road?"))
                    .andExpect(jsonPath("$[0].toolCalls").value("[{\"name\":\"getJoke\",\"result\":\"joke text\"}]"))
                    .andExpect(jsonPath("$[0].tokenUsage").value("{\"prompt_tokens\":30,\"completion_tokens\":15}"))
                    .andExpect(jsonPath("$[0].durationMs").isNumber());
        }

        @Test
        void failedExecutionContainsErrorDetails() throws Exception {
            Task task = saveTask("Error Task", Task.Status.failed);

            TaskExecution exec = TaskExecution.start(task.getId(), 1, "system", "user");
            exec = exec.withFailed("NullPointerException", "java.lang.NullPointerException\n  at Foo.bar(Foo.java:42)");
            taskExecutionRepository.save(exec);

            mockMvc.perform(get("/api/tasks/{taskId}/executions", task.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("failed"))
                    .andExpect(jsonPath("$[0].errorMessage").value("NullPointerException"))
                    .andExpect(jsonPath("$[0].errorTrace")
                            .value("java.lang.NullPointerException\n  at Foo.bar(Foo.java:42)"))
                    .andExpect(jsonPath("$[0].durationMs").isNumber());
        }

        @Test
        void executionsIsolatedByTaskId() throws Exception {
            Task taskA = saveTask("Task A", Task.Status.completed);
            Task taskB = saveTask("Task B", Task.Status.completed);

            TaskExecution execA = TaskExecution.start(taskA.getId(), 1, "sys", "prompt A");
            taskExecutionRepository.save(execA.withCompleted("resp A", null, null));

            TaskExecution execB1 = TaskExecution.start(taskB.getId(), 1, "sys", "prompt B1");
            taskExecutionRepository.save(execB1.withCompleted("resp B1", null, null));
            TaskExecution execB2 = TaskExecution.start(taskB.getId(), 2, "sys", "prompt B2");
            taskExecutionRepository.save(execB2.withCompleted("resp B2", null, null));

            mockMvc.perform(get("/api/tasks/{taskId}/executions", taskA.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));

            mockMvc.perform(get("/api/tasks/{taskId}/executions", taskB.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    @Nested
    class DeliveriesEndpoint {

        @Test
        void emptyDeliveries_returnsEmptyArray() throws Exception {
            Task task = saveTask("No Deliveries", Task.Status.completed);

            mockMvc.perform(get("/api/tasks/{taskId}/deliveries", task.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void successfulDeliveryContainsDetails() throws Exception {
            Task task = saveTask("Delivered Task", Task.Status.completed);

            deliveryAuditLogRepository.save(DeliveryAuditLog.delivered(
                    task.getId(), "conv-1", "WebChatChannel", "Task completed: Delivered Task", 1, 45L));

            mockMvc.perform(get("/api/tasks/{taskId}/deliveries", task.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].taskId").value(task.getId()))
                    .andExpect(jsonPath("$[0].conversationId").value("conv-1"))
                    .andExpect(jsonPath("$[0].channelName").value("WebChatChannel"))
                    .andExpect(jsonPath("$[0].message").value("Task completed: Delivered Task"))
                    .andExpect(jsonPath("$[0].status").value("delivered"))
                    .andExpect(jsonPath("$[0].attempts").value(1))
                    .andExpect(jsonPath("$[0].durationMs").value(45));
        }

        @Test
        void failedDeliveryContainsErrorMessage() throws Exception {
            Task task = saveTask("Failed Delivery Task", Task.Status.completed);

            deliveryAuditLogRepository.save(DeliveryAuditLog.failed(
                    task.getId(),
                    "conv-1",
                    "TelegramChannel",
                    "Task completed: Failed Delivery Task",
                    3,
                    "Telegram API timeout",
                    8500L));

            mockMvc.perform(get("/api/tasks/{taskId}/deliveries", task.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("failed"))
                    .andExpect(jsonPath("$[0].channelName").value("TelegramChannel"))
                    .andExpect(jsonPath("$[0].attempts").value(3))
                    .andExpect(jsonPath("$[0].errorMessage").value("Telegram API timeout"))
                    .andExpect(jsonPath("$[0].durationMs").value(8500));
        }

        @Test
        void multipleDeliveriesOrderedByCreatedAt() throws Exception {
            Task task = saveTask("Multi-Delivery Task", Task.Status.completed);

            deliveryAuditLogRepository.save(
                    DeliveryAuditLog.delivered(task.getId(), "conv-1", "WebChatChannel", "SSE push", 1, 10L));
            deliveryAuditLogRepository.save(DeliveryAuditLog.failed(
                    task.getId(), "conv-1", "TelegramChannel", "Telegram msg", 2, "timeout", 5000L));
            deliveryAuditLogRepository.save(DeliveryAuditLog.delivered(
                    task.getId(), "conv-1", "TelegramChannel", "Telegram msg retry", 3, 200L));

            mockMvc.perform(get("/api/tasks/{taskId}/deliveries", task.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].channelName").value("WebChatChannel"))
                    .andExpect(jsonPath("$[0].status").value("delivered"))
                    .andExpect(jsonPath("$[1].status").value("failed"))
                    .andExpect(jsonPath("$[2].status").value("delivered"));
        }

        @Test
        void deliveriesIsolatedByTaskId() throws Exception {
            Task taskA = saveTask("Task A", Task.Status.completed);
            Task taskB = saveTask("Task B", Task.Status.completed);

            deliveryAuditLogRepository.save(
                    DeliveryAuditLog.delivered(taskA.getId(), "conv-1", "WebChatChannel", "msg A", 1, 10L));
            deliveryAuditLogRepository.save(
                    DeliveryAuditLog.delivered(taskB.getId(), "conv-2", "WebChatChannel", "msg B", 1, 10L));

            mockMvc.perform(get("/api/tasks/{taskId}/deliveries", taskA.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].message").value("msg A"));
        }
    }

    @Nested
    class FullLifecycleAuditTrail {

        @Test
        void t58_fullTaskLifecycle_allAuditEventsPresent() throws Exception {
            // Create task
            Task task = saveTask("Lifecycle Task", Task.Status.completed);

            // Simulate full lifecycle audit trail
            taskAuditLogRepository.save(TaskAuditLog.taskEvent(task.getId(), "exec-1", TaskAuditLog.EVENT_CREATED));
            taskAuditLogRepository.save(TaskAuditLog.llmEvent(
                    task.getId(),
                    "exec-1",
                    "You are a weather bot",
                    "What is the weather in Moscow?",
                    "full-request",
                    "The weather is sunny, 22C",
                    "{\"prompt_tokens\":40,\"completion_tokens\":12}",
                    800L));
            taskAuditLogRepository.save(TaskAuditLog.toolCallEvent(
                    task.getId(), "exec-1", "getWeather", "{\"city\":\"Moscow\"}", "Sunny, 22C", 350L));
            taskAuditLogRepository.save(
                    TaskAuditLog.progressEvent(task.getId(), "exec-1", "{\"progress\":\"Fetching weather data\"}"));
            taskAuditLogRepository.save(TaskAuditLog.taskEvent(task.getId(), "exec-1", TaskAuditLog.EVENT_COMPLETED));

            // Simulate execution record
            TaskExecution exec = TaskExecution.start(task.getId(), 1, "You are a weather bot", "What is the weather?");
            exec = exec.withCompleted("The weather is sunny, 22C", "[{\"name\":\"getWeather\"}]", "{\"total\":52}");
            taskExecutionRepository.save(exec);

            // Simulate delivery
            deliveryAuditLogRepository.save(DeliveryAuditLog.delivered(
                    task.getId(), "conv-1", "WebChatChannel", "Weather: Sunny, 22C", 1, 25L));

            // Verify audit trail via API
            mockMvc.perform(get("/api/tasks/{taskId}/audit", task.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(5))
                    .andExpect(jsonPath("$[0].eventType").value("created"))
                    .andExpect(jsonPath("$[1].eventType").value("started"))
                    .andExpect(jsonPath("$[1].systemPrompt").value("You are a weather bot"))
                    .andExpect(jsonPath("$[1].llmResponse").value("The weather is sunny, 22C"))
                    .andExpect(jsonPath("$[2].eventType").value("tool_call"))
                    .andExpect(jsonPath("$[2].toolName").value("getWeather"))
                    .andExpect(jsonPath("$[2].toolDurationMs").value(350))
                    .andExpect(jsonPath("$[3].eventType").value("progress"))
                    .andExpect(jsonPath("$[4].eventType").value("completed"));

            // Verify execution via API
            mockMvc.perform(get("/api/tasks/{taskId}/executions", task.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].status").value("completed"))
                    .andExpect(jsonPath("$[0].llmResponse").value("The weather is sunny, 22C"));

            // Verify delivery via API
            mockMvc.perform(get("/api/tasks/{taskId}/deliveries", task.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].status").value("delivered"))
                    .andExpect(jsonPath("$[0].channelName").value("WebChatChannel"));
        }
    }
}
