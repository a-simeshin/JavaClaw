package ai.javaclaw.agent.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskAuditServiceTest {

    @Mock
    private TaskAuditLogRepository repository;

    @Captor
    private ArgumentCaptor<TaskAuditLog> captor;

    private TaskAuditService service;

    @BeforeEach
    void setUp() {
        lenient().when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        service = new TaskAuditService(repository);
    }

    @Test
    void logCreatedSavesCreatedEvent() {
        service.logCreated("task-1");
        final TaskAuditLog saved = captor.getValue();
        assertThat(saved.taskId()).isEqualTo("task-1");
        assertThat(saved.eventType()).isEqualTo(TaskAuditLog.EVENT_CREATED);
        assertThat(saved.executionId()).isNull();
        assertThat(saved.createdAt()).isNotNull();
    }

    @Test
    void logStartedSavesStartedEventWithExecutionId() {
        service.logStarted("task-1", "exec-1");
        final TaskAuditLog saved = captor.getValue();
        assertThat(saved.taskId()).isEqualTo("task-1");
        assertThat(saved.executionId()).isEqualTo("exec-1");
        assertThat(saved.eventType()).isEqualTo(TaskAuditLog.EVENT_STARTED);
    }

    @Test
    void logCompletedSavesCompletedEventWithDuration() {
        service.logCompleted("task-1", "exec-1", 1500L);
        final TaskAuditLog saved = captor.getValue();
        assertThat(saved.taskId()).isEqualTo("task-1");
        assertThat(saved.executionId()).isEqualTo("exec-1");
        assertThat(saved.eventType()).isEqualTo(TaskAuditLog.EVENT_COMPLETED);
        assertThat(saved.durationMs()).isEqualTo(1500L);
    }

    @Test
    void logFailedSavesErrorEventWithDetails() {
        service.logFailed("task-1", "exec-1", "boom", "stack trace here", 2000L);
        final TaskAuditLog saved = captor.getValue();
        assertThat(saved.taskId()).isEqualTo("task-1");
        assertThat(saved.eventType()).isEqualTo(TaskAuditLog.EVENT_FAILED);
        assertThat(saved.errorMessage()).isEqualTo("boom");
        assertThat(saved.errorTrace()).isEqualTo("stack trace here");
        assertThat(saved.durationMs()).isEqualTo(2000L);
    }

    @Test
    void logCancelledSavesCancelledEvent() {
        service.logCancelled("task-1", "exec-1");
        final TaskAuditLog saved = captor.getValue();
        assertThat(saved.taskId()).isEqualTo("task-1");
        assertThat(saved.eventType()).isEqualTo(TaskAuditLog.EVENT_CANCELLED);
    }

    @Test
    void logLlmCallSavesLlmEventWithPromptAndResponse() {
        service.logLlmCall("task-1", "exec-1", "system prompt", "user prompt", "llm response", null, 500L);
        final TaskAuditLog saved = captor.getValue();
        assertThat(saved.taskId()).isEqualTo("task-1");
        assertThat(saved.eventType()).isEqualTo(TaskAuditLog.EVENT_STARTED);
        assertThat(saved.systemPrompt()).isEqualTo("system prompt");
        assertThat(saved.userPrompt()).isEqualTo("user prompt");
        assertThat(saved.llmResponse()).isEqualTo("llm response");
        assertThat(saved.durationMs()).isEqualTo(500L);
    }

    @Test
    void logToolCallSavesToolCallEvent() {
        service.logToolCall("task-1", "exec-1", "searchWeb", "{\"q\":\"test\"}", "result", 100L);
        final TaskAuditLog saved = captor.getValue();
        assertThat(saved.taskId()).isEqualTo("task-1");
        assertThat(saved.eventType()).isEqualTo(TaskAuditLog.EVENT_TOOL_CALL);
        assertThat(saved.toolName()).isEqualTo("searchWeb");
        assertThat(saved.toolArgs()).isEqualTo("{\"q\":\"test\"}");
        assertThat(saved.toolResult()).isEqualTo("result");
        assertThat(saved.toolDurationMs()).isEqualTo(100L);
    }

    @Test
    void logProgressSavesProgressEvent() {
        service.logProgress("task-1", "exec-1", "{\"percent\":50}");
        final TaskAuditLog saved = captor.getValue();
        assertThat(saved.eventType()).isEqualTo(TaskAuditLog.EVENT_PROGRESS);
        assertThat(saved.metadata()).isEqualTo("{\"percent\":50}");
    }

    @Test
    void logApprovalRequestedSavesApprovalEvent() {
        service.logApprovalRequested("task-1", "exec-1", "{\"question\":\"buy?\"}");
        final TaskAuditLog saved = captor.getValue();
        assertThat(saved.eventType()).isEqualTo(TaskAuditLog.EVENT_APPROVAL_REQUESTED);
        assertThat(saved.metadata()).isEqualTo("{\"question\":\"buy?\"}");
    }

    @Test
    void logTimeoutSavesTimeoutEvent() {
        service.logTimeout("task-1", "exec-1");
        final TaskAuditLog saved = captor.getValue();
        assertThat(saved.eventType()).isEqualTo(TaskAuditLog.EVENT_TIMEOUT);
    }

    @Test
    void logDeliveredSavesDeliveredEvent() {
        service.logDelivered("task-1", "exec-1");
        final TaskAuditLog saved = captor.getValue();
        assertThat(saved.eventType()).isEqualTo(TaskAuditLog.EVENT_DELIVERED);
    }

    @Test
    void saveExceptionDoesNotPropagate() {
        // Reset captor-based setup — use a fresh mock setup that throws
        final TaskAuditLogRepository throwingRepo = org.mockito.Mockito.mock(TaskAuditLogRepository.class);
        when(throwingRepo.save(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("DB down"));
        final TaskAuditService failService = new TaskAuditService(throwingRepo);

        // Should not throw — error is caught and logged
        failService.logCreated("task-1");
    }
}
