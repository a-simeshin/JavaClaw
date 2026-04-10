package ai.javaclaw.tasks;

import ai.javaclaw.agent.audit.TaskAuditService;
import ai.javaclaw.agent.event.AgentEvent;
import ai.javaclaw.agent.event.EventBus;
import ai.javaclaw.agent.event.EventKind;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic watchdog that detects stuck tasks and expired approval requests.
 *
 * <p>Runs every 30 seconds to:
 * <ul>
 *   <li>Find in_progress tasks past their timeout_seconds and mark them failed (S13, T31)</li>
 *   <li>Find pending approval_requests past their timeout_at and auto-deny them (T32)</li>
 * </ul>
 */
@Component
public class TaskWatchdog {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskWatchdog.class);

    private final TaskRepository taskRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final CancellationTokenRegistry cancellationTokenRegistry;
    private final TaskAuditService taskAuditService;
    private final EventBus eventBus;

    public TaskWatchdog(
            final TaskRepository taskRepository,
            final ApprovalRequestRepository approvalRequestRepository,
            final CancellationTokenRegistry cancellationTokenRegistry,
            final TaskAuditService taskAuditService,
            final EventBus eventBus) {
        this.taskRepository = taskRepository;
        this.approvalRequestRepository = approvalRequestRepository;
        this.cancellationTokenRegistry = cancellationTokenRegistry;
        this.taskAuditService = taskAuditService;
        this.eventBus = eventBus;
    }

    @Scheduled(fixedRate = 30_000)
    public void checkTimeouts() {
        checkTimedOutTasks();
        checkExpiredApprovals();
    }

    void checkTimedOutTasks() {
        final Instant now = Instant.now();
        final List<Task> inProgressTasks = taskRepository.findByStatus(Task.Status.in_progress);

        for (final Task task : inProgressTasks) {
            if (task.getTimeoutSeconds() == null) {
                continue;
            }
            final Instant deadline = task.getUpdatedAt().plusSeconds(task.getTimeoutSeconds());
            if (now.isAfter(deadline)) {
                timeoutTask(task);
            }
        }
    }

    void checkExpiredApprovals() {
        final Instant now = Instant.now();
        final List<ApprovalRequest> expired =
                approvalRequestRepository.findByStatusAndTimeoutAtBefore(ApprovalRequest.Status.pending, now);

        for (final ApprovalRequest approval : expired) {
            timeoutApproval(approval);
        }
    }

    private void timeoutTask(final Task task) {
        try {
            final Task failed = task.withStatus(Task.Status.failed).withFeedback("Timed out");
            taskRepository.save(failed);

            cancellationTokenRegistry.cancel(task.getId());

            taskAuditService.logTimeout(task.getId(), null);

            eventBus.emit(AgentEvent.of(
                    EventKind.TASK_STATUS_CHANGE,
                    AgentEvent.EventMeta.ofTask(null, task.getId()),
                    Map.of("status", "failed", "reason", "timeout")));

            LOGGER.info(
                    "Task '{}' (id={}) timed out after {}s", task.getName(), task.getId(), task.getTimeoutSeconds());
        } catch (Exception e) {
            LOGGER.error("Failed to timeout task {}: {}", task.getId(), e.getMessage(), e);
        }
    }

    private void timeoutApproval(final ApprovalRequest approval) {
        try {
            final ApprovalRequest timedOut = approval.withTimedOut();
            approvalRequestRepository.save(timedOut);

            taskAuditService.logTimeout(approval.getTaskId(), null);

            eventBus.emit(AgentEvent.of(
                    EventKind.APPROVAL_REQUESTED,
                    AgentEvent.EventMeta.ofTask(null, approval.getTaskId()),
                    Map.of("status", "timeout", "approvalId", approval.getId())));

            LOGGER.info("Approval request {} for task {} timed out", approval.getId(), approval.getTaskId());
        } catch (Exception e) {
            LOGGER.error("Failed to timeout approval {}: {}", approval.getId(), e.getMessage(), e);
        }
    }
}
