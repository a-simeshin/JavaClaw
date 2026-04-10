package ai.javaclaw.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.audit.TaskAuditService;
import ai.javaclaw.agent.event.AgentEvent;
import ai.javaclaw.agent.event.EventBus;
import ai.javaclaw.agent.event.EventKind;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskWatchdogTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private CancellationTokenRegistry cancellationTokenRegistry;

    @Mock
    private TaskAuditService taskAuditService;

    @Mock
    private EventBus eventBus;

    @Captor
    private ArgumentCaptor<Task> taskCaptor;

    @Captor
    private ArgumentCaptor<ApprovalRequest> approvalCaptor;

    @Captor
    private ArgumentCaptor<AgentEvent> eventCaptor;

    private TaskWatchdog watchdog;

    @BeforeEach
    void setUp() {
        watchdog = new TaskWatchdog(
                taskRepository, approvalRequestRepository, cancellationTokenRegistry, taskAuditService, eventBus);
    }

    /** T31: task in_progress past timeout -> marked failed + audit logged + event emitted. */
    @Test
    void timedOutTaskMarkedFailedWithFeedback() {
        final Task stuckTask = new Task(
                "task-1",
                "Stuck Task",
                Instant.now().minusSeconds(600),
                Instant.now().minusSeconds(400), // updated 400s ago
                Task.Status.in_progress,
                "desc",
                null,
                null,
                "conv-1",
                null,
                null,
                null,
                300, // 300s timeout -> 400s ago exceeds
                null,
                "user-1",
                null,
                null,
                0);

        when(taskRepository.findByStatus(Task.Status.in_progress)).thenReturn(List.of(stuckTask));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        watchdog.checkTimedOutTasks();

        verify(taskRepository).save(taskCaptor.capture());
        final Task saved = taskCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(Task.Status.failed);
        assertThat(saved.getFeedback()).isEqualTo("Timed out");
        assertThat(saved.getFailedAt()).isNotNull();

        verify(cancellationTokenRegistry).cancel("task-1");
        verify(taskAuditService).logTimeout("task-1", null);

        verify(eventBus).emit(eventCaptor.capture());
        final AgentEvent event = eventCaptor.getValue();
        assertThat(event.kind()).isEqualTo(EventKind.TASK_STATUS_CHANGE);
        assertThat(event.meta().taskId()).isEqualTo("task-1");
        assertThat(event.payload()).containsEntry("status", "failed").containsEntry("reason", "timeout");
    }

    /** T32: approval_request past timeout -> auto-denied (timed out). */
    @Test
    void expiredApprovalTimedOut() {
        final ApprovalRequest expired = new ApprovalRequest(
                "approval-1",
                "task-2",
                "conv-1",
                "Buy ticket?",
                null,
                ApprovalRequest.Status.pending,
                Instant.now().minusSeconds(60), // expired 60s ago
                Instant.now().minusSeconds(120),
                null);

        when(approvalRequestRepository.findByStatusAndTimeoutAtBefore(
                        eq(ApprovalRequest.Status.pending), any(Instant.class)))
                .thenReturn(List.of(expired));
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        watchdog.checkExpiredApprovals();

        verify(approvalRequestRepository).save(approvalCaptor.capture());
        final ApprovalRequest saved = approvalCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ApprovalRequest.Status.timeout);
        assertThat(saved.getRespondedAt()).isNotNull();

        verify(taskAuditService).logTimeout("task-2", null);
        verify(eventBus).emit(eventCaptor.capture());
        final AgentEvent event = eventCaptor.getValue();
        assertThat(event.meta().taskId()).isEqualTo("task-2");
        assertThat(event.payload()).containsEntry("status", "timeout");
    }

    /** T33: task within timeout -> not touched. */
    @Test
    void taskWithinTimeoutNotTouched() {
        final Task activeTask = new Task(
                "task-3",
                "Active Task",
                Instant.now().minusSeconds(60),
                Instant.now().minusSeconds(10), // updated 10s ago
                Task.Status.in_progress,
                "desc",
                null,
                null,
                "conv-1",
                null,
                null,
                null,
                300, // 300s timeout -> 10s ago is within
                null,
                "user-1",
                null,
                null,
                0);

        when(taskRepository.findByStatus(Task.Status.in_progress)).thenReturn(List.of(activeTask));

        watchdog.checkTimedOutTasks();

        verify(taskRepository, never()).save(any(Task.class));
        verify(cancellationTokenRegistry, never()).cancel(any());
        verify(taskAuditService, never()).logTimeout(any(), any());
        verify(eventBus, never()).emit(any());
    }

    /** Task without timeout_seconds is never timed out. */
    @Test
    void taskWithoutTimeoutNotTouched() {
        final Task noTimeoutTask = new Task(
                "task-4",
                "No Timeout Task",
                Instant.now().minusSeconds(3600),
                Instant.now().minusSeconds(3600), // very old, but no timeout set
                Task.Status.in_progress,
                "desc",
                null,
                null,
                "conv-1",
                null,
                null,
                null,
                null, // no timeout
                null,
                "user-1",
                null,
                null,
                0);

        when(taskRepository.findByStatus(Task.Status.in_progress)).thenReturn(List.of(noTimeoutTask));

        watchdog.checkTimedOutTasks();

        verify(taskRepository, never()).save(any(Task.class));
    }

    /** No expired approvals -> nothing happens. */
    @Test
    void noExpiredApprovalsNoAction() {
        when(approvalRequestRepository.findByStatusAndTimeoutAtBefore(
                        eq(ApprovalRequest.Status.pending), any(Instant.class)))
                .thenReturn(List.of());

        watchdog.checkExpiredApprovals();

        verify(approvalRequestRepository, never()).save(any(ApprovalRequest.class));
        verify(taskAuditService, never()).logTimeout(any(), any());
    }

    /** checkTimeouts calls both check methods. */
    @Test
    void checkTimeoutsCallsBothChecks() {
        when(taskRepository.findByStatus(Task.Status.in_progress)).thenReturn(List.of());
        when(approvalRequestRepository.findByStatusAndTimeoutAtBefore(
                        eq(ApprovalRequest.Status.pending), any(Instant.class)))
                .thenReturn(List.of());

        watchdog.checkTimeouts();

        verify(taskRepository).findByStatus(Task.Status.in_progress);
        verify(approvalRequestRepository)
                .findByStatusAndTimeoutAtBefore(eq(ApprovalRequest.Status.pending), any(Instant.class));
    }

    /** Multiple timed-out tasks are all processed. */
    @Test
    void multipleTimedOutTasksAllProcessed() {
        final Task task1 = new Task(
                "task-a",
                "Task A",
                Instant.now().minusSeconds(600),
                Instant.now().minusSeconds(400),
                Task.Status.in_progress,
                "desc",
                null,
                null,
                "conv-1",
                null,
                null,
                null,
                300,
                null,
                "user-1",
                null,
                null,
                0);
        final Task task2 = new Task(
                "task-b",
                "Task B",
                Instant.now().minusSeconds(600),
                Instant.now().minusSeconds(500),
                Task.Status.in_progress,
                "desc",
                null,
                null,
                "conv-2",
                null,
                null,
                null,
                300,
                null,
                "user-2",
                null,
                null,
                0);

        when(taskRepository.findByStatus(Task.Status.in_progress)).thenReturn(List.of(task1, task2));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        watchdog.checkTimedOutTasks();

        verify(taskRepository, org.mockito.Mockito.times(2)).save(taskCaptor.capture());
        verify(cancellationTokenRegistry).cancel("task-a");
        verify(cancellationTokenRegistry).cancel("task-b");
        verify(taskAuditService).logTimeout("task-a", null);
        verify(taskAuditService).logTimeout("task-b", null);
    }

    /** Error in one task timeout does not prevent processing the next. */
    @Test
    void errorInOneTaskDoesNotBlockOthers() {
        final Task task1 = new Task(
                "task-err",
                "Failing",
                Instant.now().minusSeconds(600),
                Instant.now().minusSeconds(400),
                Task.Status.in_progress,
                "desc",
                null,
                null,
                "conv-1",
                null,
                null,
                null,
                300,
                null,
                "user-1",
                null,
                null,
                0);
        final Task task2 = new Task(
                "task-ok",
                "OK",
                Instant.now().minusSeconds(600),
                Instant.now().minusSeconds(400),
                Task.Status.in_progress,
                "desc",
                null,
                null,
                "conv-2",
                null,
                null,
                null,
                300,
                null,
                "user-2",
                null,
                null,
                0);

        when(taskRepository.findByStatus(Task.Status.in_progress)).thenReturn(List.of(task1, task2));
        when(taskRepository.save(any(Task.class)))
                .thenThrow(new RuntimeException("DB error"))
                .thenAnswer(inv -> inv.getArgument(0));

        watchdog.checkTimedOutTasks();

        // Second task should still be processed despite first failing
        verify(taskRepository, org.mockito.Mockito.times(2)).save(any(Task.class));
    }
}
