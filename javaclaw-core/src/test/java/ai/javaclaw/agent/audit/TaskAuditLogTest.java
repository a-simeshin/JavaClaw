package ai.javaclaw.agent.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskAuditLogTest {

    @Test
    void taskEvent_setsTaskIdAndEventType() {
        TaskAuditLog log = TaskAuditLog.taskEvent("task-1", "exec-1", TaskAuditLog.EVENT_CREATED);

        assertThat(log.id()).isNull();
        assertThat(log.taskId()).isEqualTo("task-1");
        assertThat(log.executionId()).isEqualTo("exec-1");
        assertThat(log.eventType()).isEqualTo("created");
        assertThat(log.createdAt()).isNotNull();
        assertThat(log.systemPrompt()).isNull();
        assertThat(log.errorMessage()).isNull();
    }

    @Test
    void errorEvent_setsErrorDetailsAndDuration() {
        TaskAuditLog log = TaskAuditLog.errorEvent("task-2", "exec-2", "NullPointerException", "stack...", 1500L);

        assertThat(log.taskId()).isEqualTo("task-2");
        assertThat(log.executionId()).isEqualTo("exec-2");
        assertThat(log.eventType()).isEqualTo(TaskAuditLog.EVENT_FAILED);
        assertThat(log.errorMessage()).isEqualTo("NullPointerException");
        assertThat(log.errorTrace()).isEqualTo("stack...");
        assertThat(log.durationMs()).isEqualTo(1500L);
    }

    @Test
    void llmEvent_setsPromptsAndResponse() {
        TaskAuditLog log = TaskAuditLog.llmEvent(
                "task-3",
                "exec-3",
                "You are an assistant",
                "Tell me a joke",
                "{\"messages\":[...]}",
                "Why did...",
                "{\"prompt\":100,\"completion\":50}",
                2000L);

        assertThat(log.taskId()).isEqualTo("task-3");
        assertThat(log.eventType()).isEqualTo(TaskAuditLog.EVENT_STARTED);
        assertThat(log.systemPrompt()).isEqualTo("You are an assistant");
        assertThat(log.userPrompt()).isEqualTo("Tell me a joke");
        assertThat(log.llmRequest()).isEqualTo("{\"messages\":[...]}");
        assertThat(log.llmResponse()).isEqualTo("Why did...");
        assertThat(log.tokenUsage()).isEqualTo("{\"prompt\":100,\"completion\":50}");
        assertThat(log.durationMs()).isEqualTo(2000L);
    }

    @Test
    void toolCallEvent_setsToolDetails() {
        TaskAuditLog log =
                TaskAuditLog.toolCallEvent("task-4", "exec-4", "weather", "{\"city\":\"Moscow\"}", "Sunny, 20C", 350L);

        assertThat(log.taskId()).isEqualTo("task-4");
        assertThat(log.eventType()).isEqualTo(TaskAuditLog.EVENT_TOOL_CALL);
        assertThat(log.toolName()).isEqualTo("weather");
        assertThat(log.toolArgs()).isEqualTo("{\"city\":\"Moscow\"}");
        assertThat(log.toolResult()).isEqualTo("Sunny, 20C");
        assertThat(log.toolDurationMs()).isEqualTo(350L);
        assertThat(log.llmRequest()).isNull();
    }

    @Test
    void progressEvent_setsMetadata() {
        TaskAuditLog log = TaskAuditLog.progressEvent("task-5", "exec-5", "{\"progress\":60,\"message\":\"3 of 5\"}");

        assertThat(log.taskId()).isEqualTo("task-5");
        assertThat(log.eventType()).isEqualTo(TaskAuditLog.EVENT_PROGRESS);
        assertThat(log.metadata()).isEqualTo("{\"progress\":60,\"message\":\"3 of 5\"}");
        assertThat(log.toolName()).isNull();
    }

    @Test
    void approvalEvent_setsEventTypeAndMetadata() {
        TaskAuditLog requested = TaskAuditLog.approvalEvent(
                "task-6", "exec-6", TaskAuditLog.EVENT_APPROVAL_REQUESTED, "{\"question\":\"Buy ticket?\"}");

        assertThat(requested.eventType()).isEqualTo("approval_requested");
        assertThat(requested.metadata()).isEqualTo("{\"question\":\"Buy ticket?\"}");

        TaskAuditLog received = TaskAuditLog.approvalEvent(
                "task-6", "exec-6", TaskAuditLog.EVENT_APPROVAL_RECEIVED, "{\"response\":\"Yes\"}");

        assertThat(received.eventType()).isEqualTo("approval_received");
        assertThat(received.metadata()).isEqualTo("{\"response\":\"Yes\"}");
    }

    @Test
    void eventTypeConstants_haveCorrectValues() {
        assertThat(TaskAuditLog.EVENT_CREATED).isEqualTo("created");
        assertThat(TaskAuditLog.EVENT_STARTED).isEqualTo("started");
        assertThat(TaskAuditLog.EVENT_TOOL_CALL).isEqualTo("tool_call");
        assertThat(TaskAuditLog.EVENT_PROGRESS).isEqualTo("progress");
        assertThat(TaskAuditLog.EVENT_APPROVAL_REQUESTED).isEqualTo("approval_requested");
        assertThat(TaskAuditLog.EVENT_APPROVAL_RECEIVED).isEqualTo("approval_received");
        assertThat(TaskAuditLog.EVENT_COMPLETED).isEqualTo("completed");
        assertThat(TaskAuditLog.EVENT_FAILED).isEqualTo("failed");
        assertThat(TaskAuditLog.EVENT_CANCELLED).isEqualTo("cancelled");
        assertThat(TaskAuditLog.EVENT_TIMEOUT).isEqualTo("timeout");
        assertThat(TaskAuditLog.EVENT_DELIVERED).isEqualTo("delivered");
    }

    @Test
    void taskEvent_withNullExecutionId_isAllowed() {
        TaskAuditLog log = TaskAuditLog.taskEvent("task-7", null, TaskAuditLog.EVENT_CREATED);

        assertThat(log.taskId()).isEqualTo("task-7");
        assertThat(log.executionId()).isNull();
        assertThat(log.eventType()).isEqualTo("created");
    }
}
