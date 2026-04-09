package ai.javaclaw.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.channels.ChannelContextService;
import ai.javaclaw.channels.ChannelRegistry;
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
    ChannelRegistry channelRegistryMock;

    @Mock
    ChannelContextService channelContextServiceMock;

    @Mock
    ConversationEnsurer conversationEnsurerMock;

    InMemoryStorageProvider storageProvider;
    TaskManager taskManager;

    @BeforeEach
    void setUp() {
        storageProvider = new InMemoryStorageProvider();
        JobScheduler jobScheduler = JobRunr.configure()
                .useStorageProvider(storageProvider)
                .useJobActivator(getJobActivator())
                .useBackgroundJobServer(
                        usingStandardBackgroundJobServerConfiguration().andPollInterval(Duration.ofMillis(200)))
                .initialize()
                .getJobScheduler();

        taskManager = new TaskManager(jobScheduler, storageProvider, taskRepositoryMock, recurringTaskRepositoryMock);
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

    private @NonNull JobActivator getJobActivator() {
        return new JobActivator() {
            @Override
            public <T> T activateJob(Class<T> type) throws JobActivatorShutdownException {
                if (TaskHandler.class.equals(type))
                    return (T) new TaskHandler(
                            agentMock,
                            taskRepositoryMock,
                            channelRegistryMock,
                            channelContextServiceMock,
                            conversationEnsurerMock);
                else if (RecurringTaskHandler.class.equals(type))
                    return (T) new RecurringTaskHandler(taskManager, recurringTaskRepositoryMock);
                else throw new IllegalStateException("Type " + type + " is unknown");
            }
        };
    }
}
