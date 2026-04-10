package ai.javaclaw.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.agent.audit.TaskAuditService;
import ai.javaclaw.agent.event.EventBus;
import ai.javaclaw.conversations.ConversationEnsurer;
import ai.javaclaw.tasks.Task.Status;
import ai.javaclaw.tasks.TaskHandler.TaskResult;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jobrunr.configuration.JobRunr;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.server.JobActivator;
import org.jobrunr.server.JobActivatorShutdownException;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.Paging;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskManagerTest {

    @Mock
    Agent agentMock;

    @Mock
    TaskRepository taskRepositoryMock;

    @Mock
    RecurringTaskRepository recurringTaskRepositoryMock;

    @Mock
    ConversationEnsurer conversationEnsurerMock;

    @Mock
    TaskExecutionRepository taskExecutionRepositoryMock;

    @Mock
    EventBus eventBusMock;

    @Mock
    TaskAuditService taskAuditServiceMock;

    @Mock
    ai.javaclaw.delivery.DeliveryService deliveryServiceMock;

    @Mock
    TaskRateLimiter rateLimiterMock;

    CancellationTokenRegistry cancellationTokenRegistry;

    InMemoryStorageProvider storageProvider;
    TaskManager taskManager;

    @BeforeEach
    void setUp() {
        cancellationTokenRegistry = new CancellationTokenRegistry();
        storageProvider = new InMemoryStorageProvider();
        JobScheduler jobScheduler = JobRunr.configure()
                .useStorageProvider(storageProvider)
                .useJobActivator(getJobActivator())
                .useBackgroundJobServer(
                        usingStandardBackgroundJobServerConfiguration().andPollInterval(Duration.ofMillis(200)))
                .initialize()
                .getJobScheduler();

        taskManager = new TaskManager(
                jobScheduler,
                storageProvider,
                taskRepositoryMock,
                recurringTaskRepositoryMock,
                cancellationTokenRegistry,
                eventBusMock,
                taskAuditServiceMock,
                rateLimiterMock);
    }

    @AfterEach
    void tearDown() {
        JobRunr.destroy();
    }

    @Test
    void createEnqueuesJob() {
        Task saved = new Task(
                "some-id",
                "handle-email",
                Instant.now(),
                Instant.now(),
                Task.Status.todo,
                "Process unread email messages",
                null,
                null,
                null);
        when(taskRepositoryMock.save(any(Task.class))).thenReturn(saved);
        when(taskRepositoryMock.findById("some-id")).thenReturn(Optional.of(saved));
        when(taskExecutionRepositoryMock.findByTaskIdOrderByExecutionNumberDesc(anyString()))
                .thenReturn(List.of());
        when(taskExecutionRepositoryMock.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agentMock.prompt(eq("some-id"), anyString(), any()))
                .thenReturn(new TaskResult(Status.completed, "All mail was summarized!"));

        taskManager.create("handle-email", "Process unread email messages");

        await().until(() -> storageProvider.countJobs(StateName.SUCCEEDED) == 1);
    }

    @Test
    void scheduleRegistersScheduledJob() {
        LocalDateTime executionTime =
                LocalDateTime.now().plusMinutes(5).withSecond(0).withNano(0);
        Task saved = new Task(
                "some-id",
                "weekly-summary",
                Instant.now(),
                Instant.now(),
                Task.Status.todo,
                "Prepare the weekly summary",
                null,
                null,
                null);
        when(taskRepositoryMock.save(any(Task.class))).thenReturn(saved);

        taskManager.schedule(executionTime, "weekly-summary", "Prepare the weekly summary");

        await().until(() -> storageProvider.countJobs(StateName.SCHEDULED) == 1);
    }

    @Test
    void scheduleRecurrentlyRegistersRecurringJob() {
        String cronExpression = "0 */15 * * *";
        RecurringTask saved = new RecurringTask(
                "some-id", "check-mail", "Check the inbox every 15 minutes", cronExpression, null, null, Instant.now());
        when(recurringTaskRepositoryMock.save(any(RecurringTask.class))).thenReturn(saved);

        taskManager.scheduleRecurrently(cronExpression, "check-mail", "Check the inbox every 15 minutes");

        await().untilAsserted(() -> {
            var recurringJobs = storageProvider.getRecurringJobs();
            assertThat(recurringJobs).hasSize(1);
            assertThat(recurringJobs.getFirst().getId()).isEqualTo("check-mail");
            assertThat(recurringJobs.getFirst().getScheduleExpression()).isEqualTo(cronExpression);
        });
    }

    @Test
    void deleteRecurringTaskRemovesFromJobRunr() {
        String cronExpression = "0 */15 * * *";
        RecurringTask saved = new RecurringTask(
                "some-id", "check-mail", "Check the inbox every 15 minutes", cronExpression, null, null, Instant.now());
        when(recurringTaskRepositoryMock.save(any(RecurringTask.class))).thenReturn(saved);
        when(recurringTaskRepositoryMock.findAll()).thenReturn(List.of(saved));

        taskManager.scheduleRecurrently(cronExpression, "check-mail", "Check the inbox every 15 minutes");

        await().untilAsserted(() -> {
            var recurringJobs = storageProvider.getRecurringJobs();
            assertThat(recurringJobs).hasSize(1);
        });

        taskManager.deleteRecurringTask("check-mail");

        await().untilAsserted(
                        () -> assertThat(storageProvider.getRecurringJobs()).isEmpty());
        await().untilAsserted(() -> assertThat(
                        storageProvider.getJobList(StateName.SCHEDULED, Paging.AmountBasedList.ascOnCreatedAt(100)))
                .isEmpty());
        verify(recurringTaskRepositoryMock).deleteById("some-id");
    }

    @Test
    void scheduleRecurrentlyWithConversationIdSavesItToRecurringTask() {
        final String cronExpression = "0 */15 * * *";
        final String conversationId = "conv-abc-123";
        final RecurringTask saved = new RecurringTask(
                "some-id",
                "check-mail",
                "Check the inbox every 15 minutes",
                cronExpression,
                null,
                conversationId,
                Instant.now());
        when(recurringTaskRepositoryMock.save(any(RecurringTask.class))).thenReturn(saved);

        taskManager.scheduleRecurrently(
                cronExpression, "check-mail", "Check the inbox every 15 minutes", conversationId);

        final ArgumentCaptor<RecurringTask> captor = ArgumentCaptor.forClass(RecurringTask.class);
        verify(recurringTaskRepositoryMock).save(captor.capture());
        assertThat(captor.getValue().getConversationId()).isEqualTo(conversationId);
    }

    @Test
    void scheduleRecurrentlyWithoutConversationIdPassesNull() {
        final String cronExpression = "0 9 * * *";
        final RecurringTask saved = new RecurringTask(
                "some-id", "daily-check", "Daily inbox check", cronExpression, null, null, Instant.now());
        when(recurringTaskRepositoryMock.save(any(RecurringTask.class))).thenReturn(saved);

        taskManager.scheduleRecurrently(cronExpression, "daily-check", "Daily inbox check");

        final ArgumentCaptor<RecurringTask> captor = ArgumentCaptor.forClass(RecurringTask.class);
        verify(recurringTaskRepositoryMock).save(captor.capture());
        assertThat(captor.getValue().getConversationId()).isNull();
    }

    @Test
    void createTaskFromRecurringTaskPropagatesConversationId() {
        final String conversationId = "conv-xyz-456";
        final RecurringTask recurringTask = new RecurringTask(
                "rt-id", "notify-user", "Send user notification", "0 10 * * *", "job-1", conversationId, Instant.now());
        final Task savedTask = new Task(
                "task-id",
                "notify-user",
                Instant.now(),
                Instant.now(),
                Task.Status.todo,
                "Send user notification",
                null,
                null,
                conversationId);
        when(taskRepositoryMock.save(any(Task.class))).thenReturn(savedTask);

        taskManager.createTaskFromRecurringTask(recurringTask);

        final ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepositoryMock).save(captor.capture());
        assertThat(captor.getValue().getConversationId()).isEqualTo(conversationId);
    }

    @Test
    void createTaskFromRecurringTaskWithNullConversationIdCreatesTaskWithNull() {
        final RecurringTask recurringTask =
                new RecurringTask("rt-id", "cleanup", "Cleanup temp files", "0 0 * * *", "job-2", null, Instant.now());
        final Task savedTask = new Task(
                "task-id",
                "cleanup",
                Instant.now(),
                Instant.now(),
                Task.Status.todo,
                "Cleanup temp files",
                null,
                null,
                null);
        when(taskRepositoryMock.save(any(Task.class))).thenReturn(savedTask);

        taskManager.createTaskFromRecurringTask(recurringTask);

        final ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepositoryMock).save(captor.capture());
        assertThat(captor.getValue().getConversationId()).isNull();
    }

    // --- T9: spawn sets parentTaskId and inherits conversationId from parent ---
    @Test
    void spawnSetsParentTaskIdAndInheritsConversationId() {
        final String parentId = "parent-id";
        final String convId = "conv-123";
        final Task parent = new Task(
                parentId,
                "parent-task",
                Instant.now(),
                Instant.now(),
                Status.in_progress,
                "Parent desc",
                null,
                null,
                convId,
                null,
                null,
                null,
                null,
                null,
                "user-1",
                null,
                null,
                null);
        final Task savedChild = new Task(
                "child-id",
                "child-task",
                Instant.now(),
                Instant.now(),
                Status.todo,
                "Child desc",
                null,
                null,
                convId,
                parentId,
                null,
                TaskRuntime.async,
                null,
                null,
                "user-1",
                null,
                null,
                null);

        when(taskRepositoryMock.findById(parentId)).thenReturn(Optional.of(parent));
        when(taskRepositoryMock.save(any(Task.class))).thenReturn(savedChild);

        Task result = taskManager.spawn(parentId, "child-task", "Child desc");

        assertThat(result.getParentTaskId()).isEqualTo(parentId);
        assertThat(result.getConversationId()).isEqualTo(convId);
        assertThat(result.getUserId()).isEqualTo("user-1");

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepositoryMock).save(captor.capture());
        assertThat(captor.getValue().getParentTaskId()).isEqualTo(parentId);
        assertThat(captor.getValue().getConversationId()).isEqualTo(convId);
        assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
        assertThat(captor.getValue().getRuntimeType()).isEqualTo(TaskRuntime.async);
    }

    // --- T10: spawn with depth > 3 throws ---
    @Test
    void spawnThrowsWhenDepthExceedsLimit() {
        // Chain: root(0) → child(1) → grandchild(2) → great-grandchild(3), try to spawn under great-grandchild
        final Task greatGrandchild = new Task(
                "ggc-id",
                "ggc",
                Instant.now(),
                Instant.now(),
                Status.in_progress,
                "GGC",
                null,
                null,
                null,
                "grandchild-id",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        final Task grandchild = new Task(
                "grandchild-id",
                "grandchild",
                Instant.now(),
                Instant.now(),
                Status.in_progress,
                "GC",
                null,
                null,
                null,
                "child-id",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        final Task child = new Task(
                "child-id",
                "child",
                Instant.now(),
                Instant.now(),
                Status.in_progress,
                "C",
                null,
                null,
                null,
                "root-id",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        final Task root = new Task(
                "root-id",
                "root",
                Instant.now(),
                Instant.now(),
                Status.in_progress,
                "R",
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
                null);

        when(taskRepositoryMock.findById("ggc-id")).thenReturn(Optional.of(greatGrandchild));
        when(taskRepositoryMock.findById("grandchild-id")).thenReturn(Optional.of(grandchild));
        when(taskRepositoryMock.findById("child-id")).thenReturn(Optional.of(child));
        when(taskRepositoryMock.findById("root-id")).thenReturn(Optional.of(root));

        assertThatThrownBy(() -> taskManager.spawn("ggc-id", "too-deep", "Should fail"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maximum task depth of 3 exceeded");
    }

    // --- T11: cancel sets cancelled status, cancels token, emits event ---
    @Test
    void cancelSetsStatusAndCancelsToken() {
        final String taskId = "cancel-me";
        final Task task = new Task(
                taskId,
                "cancel-task",
                Instant.now(),
                Instant.now(),
                Status.in_progress,
                "To cancel",
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
                null);
        when(taskRepositoryMock.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepositoryMock.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        // Register a token so we can verify it gets cancelled
        CancellationToken token = cancellationTokenRegistry.register(taskId);
        assertThat(token.isCancelled()).isFalse();

        taskManager.cancel(taskId);

        // Token should be cancelled
        assertThat(token.isCancelled()).isTrue();

        // Task should be saved with cancelled status
        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepositoryMock).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(Status.cancelled);

        // Event emitted
        verify(eventBusMock).emit(any());

        // Audit logged
        verify(taskAuditServiceMock).logCancelled(eq(taskId), any());
    }

    @Test
    void cancelIgnoresTerminalState() {
        final String taskId = "already-done";
        final Task task = new Task(
                taskId,
                "done-task",
                Instant.now(),
                Instant.now(),
                Status.completed,
                "Done",
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
                null);
        when(taskRepositoryMock.findById(taskId)).thenReturn(Optional.of(task));

        taskManager.cancel(taskId);

        verify(taskRepositoryMock, never()).save(any());
        verify(eventBusMock, never()).emit(any());
    }

    @Test
    void spawnAtDepth1Succeeds() {
        // Root task (depth=0) → spawn child (depth=1) should succeed
        final Task root = new Task(
                "root-id",
                "root",
                Instant.now(),
                Instant.now(),
                Status.in_progress,
                "Root",
                null,
                null,
                "conv-1",
                null,
                null,
                null,
                null,
                null,
                "user-1",
                null,
                null,
                null);
        final Task savedChild = new Task(
                "child-id",
                "child",
                Instant.now(),
                Instant.now(),
                Status.todo,
                "Child",
                null,
                null,
                "conv-1",
                "root-id",
                null,
                TaskRuntime.async,
                null,
                null,
                "user-1",
                null,
                null,
                null);

        when(taskRepositoryMock.findById("root-id")).thenReturn(Optional.of(root));
        when(taskRepositoryMock.save(any(Task.class))).thenReturn(savedChild);

        Task result = taskManager.spawn("root-id", "child", "Child");
        assertThat(result.getId()).isEqualTo("child-id");
    }

    @Test
    void spawnAtDepth2Succeeds() {
        // Root → child (depth=1) → spawn grandchild (depth=2) should succeed (limit is 3)
        final Task child = new Task(
                "child-id",
                "child",
                Instant.now(),
                Instant.now(),
                Status.in_progress,
                "C",
                null,
                null,
                "conv-1",
                "root-id",
                null,
                null,
                null,
                null,
                "user-1",
                null,
                null,
                null);
        final Task root = new Task(
                "root-id",
                "root",
                Instant.now(),
                Instant.now(),
                Status.in_progress,
                "R",
                null,
                null,
                "conv-1",
                null,
                null,
                null,
                null,
                null,
                "user-1",
                null,
                null,
                null);
        final Task savedGrandchild = new Task(
                "gc-id",
                "grandchild",
                Instant.now(),
                Instant.now(),
                Status.todo,
                "GC",
                null,
                null,
                "conv-1",
                "child-id",
                null,
                TaskRuntime.async,
                null,
                null,
                "user-1",
                null,
                null,
                null);

        when(taskRepositoryMock.findById("child-id")).thenReturn(Optional.of(child));
        when(taskRepositoryMock.findById("root-id")).thenReturn(Optional.of(root));
        when(taskRepositoryMock.save(any(Task.class))).thenReturn(savedGrandchild);

        Task result = taskManager.spawn("child-id", "grandchild", "GC");
        assertThat(result.getId()).isEqualTo("gc-id");
    }

    @Test
    void spawnWithNonExistentParentThrows() {
        when(taskRepositoryMock.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskManager.spawn("missing", "child", "desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Parent task not found");
    }

    @Test
    void cancelWithNonExistentTaskThrows() {
        when(taskRepositoryMock.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskManager.cancel("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Task not found");
    }

    // --- T13: create with userId → rateLimiter.checkLimit called → throws when exceeded ---
    @Test
    void createWithUserIdChecksRateLimit() {
        final String userId = "user-1";
        final Task saved = new Task(
                "task-id",
                "task",
                Instant.now(),
                Instant.now(),
                Status.todo,
                "desc",
                null,
                null,
                "conv-1",
                null,
                null,
                null,
                null,
                null,
                userId,
                null,
                null,
                null);
        when(taskRepositoryMock.save(any(Task.class))).thenReturn(saved);

        taskManager.create("task", "desc", "conv-1", userId);

        verify(rateLimiterMock).checkLimit(userId);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepositoryMock).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void createWithUserIdThrowsWhenRateLimitExceeded() {
        final String userId = "user-1";
        Mockito.doThrow(new RateLimitExceededException(userId, "max_concurrent_tasks", 10, 10))
                .when(rateLimiterMock)
                .checkLimit(userId);

        assertThatThrownBy(() -> taskManager.create("task", "desc", "conv-1", userId))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("max_concurrent_tasks");

        verify(taskRepositoryMock, never()).save(any());
    }

    @Test
    void createWithoutUserIdSkipsRateLimit() {
        final Task saved =
                new Task("task-id", "task", Instant.now(), Instant.now(), Status.todo, "desc", null, null, "conv-1");
        when(taskRepositoryMock.save(any(Task.class))).thenReturn(saved);

        taskManager.create("task", "desc", "conv-1", null);

        verify(rateLimiterMock, never()).checkLimit(any());
    }

    @Test
    void scheduleRecurrentlyWithUserIdChecksRecurringRateLimit() {
        final String userId = "user-1";
        final String cron = "0 9 * * *";
        final RecurringTask saved =
                new RecurringTask("rt-id", "daily", "Daily task", cron, null, "conv-1", Instant.now());
        when(recurringTaskRepositoryMock.save(any(RecurringTask.class))).thenReturn(saved);

        taskManager.scheduleRecurrently(cron, "daily", "Daily task", "conv-1", userId);

        verify(rateLimiterMock).checkRecurringLimit(userId);
    }

    @Test
    void spawnChecksRateLimitWithParentUserId() {
        final String parentId = "parent-id";
        final String userId = "user-1";
        final Task parent = new Task(
                parentId,
                "parent",
                Instant.now(),
                Instant.now(),
                Status.in_progress,
                "Parent",
                null,
                null,
                "conv-1",
                null,
                null,
                null,
                null,
                null,
                userId,
                null,
                null,
                null);
        final Task savedChild = new Task(
                "child-id",
                "child",
                Instant.now(),
                Instant.now(),
                Status.todo,
                "Child",
                null,
                null,
                "conv-1",
                parentId,
                null,
                TaskRuntime.async,
                null,
                null,
                userId,
                null,
                null,
                null);

        when(taskRepositoryMock.findById(parentId)).thenReturn(Optional.of(parent));
        when(taskRepositoryMock.save(any(Task.class))).thenReturn(savedChild);

        taskManager.spawn(parentId, "child", "Child");

        verify(rateLimiterMock).checkLimit(userId);
    }

    private @NonNull JobActivator getJobActivator() {
        return new JobActivator() {
            @Override
            public <T> T activateJob(Class<T> type) throws JobActivatorShutdownException {
                if (TaskHandler.class.equals(type))
                    return (T) new TaskHandler(
                            agentMock,
                            taskRepositoryMock,
                            taskExecutionRepositoryMock,
                            conversationEnsurerMock,
                            new CancellationTokenRegistry(),
                            eventBusMock,
                            taskAuditServiceMock,
                            deliveryServiceMock);
                else if (RecurringTaskHandler.class.equals(type))
                    return (T) new RecurringTaskHandler(taskManager, recurringTaskRepositoryMock);
                else throw new IllegalStateException("Type " + type + " is unknown");
            }
        };
    }
}
