package ai.javaclaw.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.agent.audit.TaskAuditService;
import ai.javaclaw.agent.event.AgentEvent;
import ai.javaclaw.agent.event.EventBus;
import ai.javaclaw.agent.event.EventKind;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelContextService;
import ai.javaclaw.channels.ChannelRegistry;
import ai.javaclaw.channels.RoutingContext;
import ai.javaclaw.conversations.ConversationEnsurer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskHandlerTest {

    @Mock
    private Agent agent;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskExecutionRepository taskExecutionRepository;

    @Mock
    private ChannelRegistry channelRegistry;

    @Mock
    private ChannelContextService channelContextService;

    @Mock
    private ConversationEnsurer conversationEnsurer;

    @Mock
    private Channel channel;

    @Mock
    private EventBus eventBus;

    @Mock
    private TaskAuditService taskAuditService;

    @Captor
    private ArgumentCaptor<AgentEvent> eventCaptor;

    @Captor
    private ArgumentCaptor<TaskExecution> executionCaptor;

    private CancellationTokenRegistry cancellationTokenRegistry;

    private TaskHandler taskHandler;

    @BeforeEach
    void setUp() {
        cancellationTokenRegistry = new CancellationTokenRegistry();
        taskHandler = new TaskHandler(
                agent,
                taskRepository,
                taskExecutionRepository,
                channelRegistry,
                channelContextService,
                conversationEnsurer,
                cancellationTokenRegistry,
                eventBus,
                taskAuditService);
    }

    @Test
    void notifiesUserViaRoutingContextWhenConversationIdPresent() {
        final Task task = taskWithConversationId("conv-42");
        final RoutingContext ctx = new RoutingContext("TelegramChannel", Map.of("chatId", "42"));
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc(anyString()))
                .thenReturn(List.of());
        when(taskExecutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agent.prompt(anyString(), anyString(), any()))
                .thenReturn(new TaskHandler.TaskResult(Task.Status.completed, "Done!"));
        when(channelContextService.getContext("conv-42")).thenReturn(Optional.of(ctx));
        when(channelRegistry.getChannel("TelegramChannel")).thenReturn(channel);

        taskHandler.executeTask("task-1");

        verify(channel).sendMessage(eq(ctx), argThat(msg -> msg.contains("completed") || msg.contains("Done!")));
    }

    @Test
    void fallsBackToSourceChannelNameWhenNoConversationId() {
        final Task task = taskWithSourceChannelName("TelegramChannel");
        when(taskRepository.findById("task-2")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc(anyString()))
                .thenReturn(List.of());
        when(taskExecutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agent.prompt(anyString(), anyString(), any()))
                .thenReturn(new TaskHandler.TaskResult(Task.Status.completed, "Done!"));
        when(channelContextService.getContext(null)).thenReturn(Optional.empty());
        when(channelRegistry.getChannel("TelegramChannel")).thenReturn(channel);

        taskHandler.executeTask("task-2");

        verify(channel).sendMessage(any(RoutingContext.class), anyString());
    }

    @Test
    void doesNotSendWhenNoChannelFound() {
        final Task task = taskWithConversationId("conv-99");
        when(taskRepository.findById("task-3")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc(anyString()))
                .thenReturn(List.of());
        when(taskExecutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agent.prompt(anyString(), anyString(), any()))
                .thenReturn(new TaskHandler.TaskResult(Task.Status.completed, "Done!"));
        when(channelContextService.getContext("conv-99")).thenReturn(Optional.empty());
        when(channelRegistry.getChannel(null)).thenReturn(null);

        taskHandler.executeTask("task-3");

        verify(channel, never()).sendMessage(any(RoutingContext.class), anyString());
    }

    @Test
    void doesNotNotifyForInProgressStatus() {
        final Task task = taskWithConversationId("conv-42");
        when(taskRepository.findById("task-4")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc(anyString()))
                .thenReturn(List.of());
        when(taskExecutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agent.prompt(anyString(), anyString(), any()))
                .thenReturn(new TaskHandler.TaskResult(Task.Status.in_progress, "Still working"));

        taskHandler.executeTask("task-4");

        verify(channel, never()).sendMessage(any(RoutingContext.class), anyString());
    }

    /** T1: executeTask → prompt goes to task_executions, NOT to spring_ai_chat_memory */
    @Test
    void savesPromptToTaskExecution() {
        final Task task = taskWithConversationId("conv-1");
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc("task-1"))
                .thenReturn(List.of());
        when(taskExecutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agent.prompt(anyString(), anyString(), any()))
                .thenReturn(new TaskHandler.TaskResult(Task.Status.completed, "Result"));

        taskHandler.executeTask("task-1");

        // Verify TaskExecution.start() was saved with user prompt
        verify(taskExecutionRepository)
                .save(argThat(exec -> exec.getStatus() == TaskExecution.Status.running
                        && exec.getUserPrompt() != null
                        && exec.getUserPrompt().contains("Do something")
                        && exec.getTaskId().equals("task-1")
                        && exec.getExecutionNumber() == 1));

        // Verify completed execution was saved with feedback
        verify(taskExecutionRepository)
                .save(argThat(exec -> exec.getStatus() == TaskExecution.Status.completed
                        && exec.getLlmResponse() != null
                        && exec.getLlmResponse().equals("Result")));
    }

    /** T3: executeTask → CancellationToken checked → throws on cancel */
    @Test
    void cancellationTokenStopsExecution() {
        final Task task = taskWithConversationId("conv-1");
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        // Cancel the token after TaskHandler registers it (via save answer, which runs after register)
        when(taskRepository.save(any())).thenAnswer(inv -> {
            cancellationTokenRegistry.cancel("task-1");
            return inv.getArgument(0);
        });
        when(taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc("task-1"))
                .thenReturn(List.of());
        when(taskExecutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        taskHandler.executeTask("task-1");

        // Should NOT call agent.prompt — task was cancelled before LLM call
        verify(agent, never()).prompt(anyString(), anyString(), any());

        // Should save cancelled status to task
        verify(taskRepository).save(argThat(t -> t.getStatus() == Task.Status.cancelled));

        // Should save cancelled execution
        verify(taskExecutionRepository).save(argThat(exec -> exec.getStatus() == TaskExecution.Status.cancelled));

        // Token should be removed from registry
        assertThat(cancellationTokenRegistry.get("task-1")).isEmpty();
    }

    /** T4: executeTask → error → task.status=failed + error in task_executions */
    @Test
    void errorSetsFailedStatusAndSavesErrorToExecution() {
        final Task task = taskWithConversationId("conv-1");
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc("task-1"))
                .thenReturn(List.of());
        when(taskExecutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agent.prompt(anyString(), anyString(), any())).thenThrow(new RuntimeException("LLM timeout"));

        assertThatThrownBy(() -> taskHandler.executeTask("task-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("LLM timeout");

        // Task should be saved with failed status (not reset to todo)
        verify(taskRepository).save(argThat(t -> t.getStatus() == Task.Status.failed));

        // Execution should be saved with error details
        verify(taskExecutionRepository)
                .save(argThat(exec -> exec.getStatus() == TaskExecution.Status.failed
                        && "LLM timeout".equals(exec.getErrorMessage())
                        && exec.getErrorTrace() != null
                        && exec.getErrorTrace().contains("RuntimeException")));

        // Token should be removed from registry
        assertThat(cancellationTokenRegistry.get("task-1")).isEmpty();
    }

    /** T5: executeTask → emits TURN_START, LLM_REQUEST, LLM_RESPONSE, TASK_STATUS_CHANGE, TURN_END events */
    @Test
    void emitsExpectedEventsOnSuccessfulExecution() {
        final Task task = taskWithConversationId("conv-1");
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc("task-1"))
                .thenReturn(List.of());
        when(taskExecutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agent.prompt(anyString(), anyString(), any()))
                .thenReturn(new TaskHandler.TaskResult(Task.Status.completed, "Done"));

        taskHandler.executeTask("task-1");

        verify(eventBus, org.mockito.Mockito.atLeast(5)).emit(eventCaptor.capture());

        final List<EventKind> kinds =
                eventCaptor.getAllValues().stream().map(AgentEvent::kind).toList();

        assertThat(kinds)
                .containsSubsequence(
                        EventKind.TURN_START,
                        EventKind.TASK_STATUS_CHANGE,
                        EventKind.LLM_REQUEST,
                        EventKind.LLM_RESPONSE,
                        EventKind.TASK_STATUS_CHANGE,
                        EventKind.TURN_END);

        // All events should have taskId in meta
        assertThat(eventCaptor.getAllValues())
                .allSatisfy(event -> assertThat(event.meta().taskId()).isEqualTo("task-1"));
    }

    /** T5 variant: executeTask error → emits ERROR + TURN_END events */
    @Test
    void emitsErrorAndTurnEndEventsOnFailure() {
        final Task task = taskWithConversationId("conv-1");
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc("task-1"))
                .thenReturn(List.of());
        when(taskExecutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agent.prompt(anyString(), anyString(), any())).thenThrow(new RuntimeException("boom"));

        try {
            taskHandler.executeTask("task-1");
        } catch (RuntimeException ignored) {
        }

        verify(eventBus, org.mockito.Mockito.atLeast(4)).emit(eventCaptor.capture());

        final List<EventKind> kinds =
                eventCaptor.getAllValues().stream().map(AgentEvent::kind).toList();

        assertThat(kinds).contains(EventKind.TURN_START, EventKind.ERROR, EventKind.TURN_END);
    }

    /** T5 variant: cancellation → emits TASK_CANCELLED + TURN_END events */
    @Test
    void emitsCancelledAndTurnEndEventsOnCancellation() {
        final Task task = taskWithConversationId("conv-1");
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> {
            cancellationTokenRegistry.cancel("task-1");
            return inv.getArgument(0);
        });
        when(taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc("task-1"))
                .thenReturn(List.of());
        when(taskExecutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        taskHandler.executeTask("task-1");

        verify(eventBus, org.mockito.Mockito.atLeast(3)).emit(eventCaptor.capture());

        final List<EventKind> kinds =
                eventCaptor.getAllValues().stream().map(AgentEvent::kind).toList();

        assertThat(kinds).contains(EventKind.TURN_START, EventKind.TASK_CANCELLED, EventKind.TURN_END);
        assertThat(kinds).doesNotContain(EventKind.ERROR);
    }

    /** Execution number increments based on existing executions */
    @Test
    void executionNumberIncrementsFromExisting() {
        final Task task = taskWithConversationId("conv-1");
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Simulate an existing execution with number 2
        final TaskExecution existing = TaskExecution.start("task-1", 2, null, "old prompt");
        when(taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc("task-1"))
                .thenReturn(List.of(existing));
        when(taskExecutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agent.prompt(anyString(), anyString(), any()))
                .thenReturn(new TaskHandler.TaskResult(Task.Status.completed, "Done"));

        taskHandler.executeTask("task-1");

        // First save should be execution #3
        verify(taskExecutionRepository)
                .save(argThat(
                        exec -> exec.getStatus() == TaskExecution.Status.running && exec.getExecutionNumber() == 3));
    }

    private Task taskWithConversationId(final String conversationId) {
        return new Task(
                "task-1",
                "test-task",
                Instant.now(),
                Instant.now(),
                Task.Status.todo,
                "Do something",
                null,
                null,
                conversationId);
    }

    /** Audit: successful execution logs started + llmCall + completed */
    @Test
    void auditLogsStartedLlmCallAndCompletedOnSuccess() {
        final Task task = taskWithConversationId("conv-1");
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc("task-1"))
                .thenReturn(List.of());
        when(taskExecutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agent.prompt(anyString(), anyString(), any()))
                .thenReturn(new TaskHandler.TaskResult(Task.Status.completed, "Done"));

        taskHandler.executeTask("task-1");

        verify(taskAuditService).logStarted(eq("task-1"), any());
        verify(taskAuditService)
                .logLlmCall(
                        eq("task-1"), any(), any(), argThat(p -> p.contains("Do something")), eq("Done"), any(), any());
        verify(taskAuditService).logCompleted(eq("task-1"), any(), any());
        verify(taskAuditService, never()).logFailed(any(), any(), any(), any(), any());
        verify(taskAuditService, never()).logCancelled(any(), any());
    }

    /** Audit: failed execution logs started + failed (no completed) */
    @Test
    void auditLogsStartedAndFailedOnError() {
        final Task task = taskWithConversationId("conv-1");
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc("task-1"))
                .thenReturn(List.of());
        when(taskExecutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agent.prompt(anyString(), anyString(), any())).thenThrow(new RuntimeException("LLM error"));

        try {
            taskHandler.executeTask("task-1");
        } catch (RuntimeException ignored) {
        }

        verify(taskAuditService).logStarted(eq("task-1"), any());
        verify(taskAuditService)
                .logFailed(eq("task-1"), any(), eq("LLM error"), argThat(t -> t.contains("RuntimeException")), any());
        verify(taskAuditService, never()).logCompleted(any(), any(), any());
        verify(taskAuditService, never()).logCancelled(any(), any());
    }

    /** Audit: cancelled execution logs started + cancelled (no completed, no failed) */
    @Test
    void auditLogsStartedAndCancelledOnCancellation() {
        final Task task = taskWithConversationId("conv-1");
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> {
            cancellationTokenRegistry.cancel("task-1");
            return inv.getArgument(0);
        });
        when(taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc("task-1"))
                .thenReturn(List.of());
        when(taskExecutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        taskHandler.executeTask("task-1");

        verify(taskAuditService).logStarted(eq("task-1"), any());
        verify(taskAuditService).logCancelled(eq("task-1"), any());
        verify(taskAuditService, never()).logCompleted(any(), any(), any());
        verify(taskAuditService, never()).logFailed(any(), any(), any(), any(), any());
    }

    private Task taskWithSourceChannelName(final String sourceChannelName) {
        return new Task(
                "task-2",
                "test-task",
                Instant.now(),
                Instant.now(),
                Task.Status.todo,
                "Do something",
                null,
                sourceChannelName,
                null);
    }
}
