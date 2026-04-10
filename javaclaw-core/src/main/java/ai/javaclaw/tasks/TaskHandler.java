package ai.javaclaw.tasks;

import static ai.javaclaw.tasks.Task.Status.awaiting_human_input;
import static ai.javaclaw.tasks.Task.Status.completed;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.agent.audit.TaskAuditService;
import ai.javaclaw.agent.event.AgentEvent;
import ai.javaclaw.agent.event.EventBus;
import ai.javaclaw.agent.event.EventKind;
import ai.javaclaw.conversations.ConversationEnsurer;
import ai.javaclaw.delivery.DeliveryService;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TaskHandler {

    private static final Logger LOGGER = new JobRunrDashboardLogger(LoggerFactory.getLogger(TaskHandler.class));

    private final Agent agent;
    private final TaskRepository taskRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final ConversationEnsurer conversationEnsurer;
    private final CancellationTokenRegistry cancellationTokenRegistry;
    private final EventBus eventBus;
    private final TaskAuditService taskAuditService;
    private final DeliveryService deliveryService;

    public TaskHandler(
            final Agent agent,
            final TaskRepository taskRepository,
            final TaskExecutionRepository taskExecutionRepository,
            final ConversationEnsurer conversationEnsurer,
            final CancellationTokenRegistry cancellationTokenRegistry,
            final EventBus eventBus,
            final TaskAuditService taskAuditService,
            final DeliveryService deliveryService) {
        this.agent = agent;
        this.taskRepository = taskRepository;
        this.taskExecutionRepository = taskExecutionRepository;
        this.conversationEnsurer = conversationEnsurer;
        this.cancellationTokenRegistry = cancellationTokenRegistry;
        this.eventBus = eventBus;
        this.taskAuditService = taskAuditService;
        this.deliveryService = deliveryService;
    }

    @Job(name = "%0", retries = 3)
    public void executeTask(final String taskId) {
        final Task task = taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));

        if (!Task.Status.todo.equals(task.getStatus())) {
            throw new IllegalStateException("Cannot handle task '" + task.getName() + "' with status "
                    + task.getStatus() + ". Only tasks that have status todo can be run");
        }

        final CancellationToken cancellationToken = cancellationTokenRegistry.register(taskId);
        final Task inProgress = taskRepository.save(task.withStatus(Task.Status.in_progress));
        final AgentEvent.EventMeta meta = AgentEvent.EventMeta.ofTask(null, taskId);

        eventBus.emit(AgentEvent.of(EventKind.TURN_START, meta));
        eventBus.emit(
                AgentEvent.of(EventKind.TASK_STATUS_CHANGE, meta, Map.of("status", Task.Status.in_progress.name())));

        final String conversationId = inProgress.getConversationId() != null ? inProgress.getConversationId() : taskId;
        final String agentInput = formatTaskForAgent(inProgress);
        final int executionNumber = nextExecutionNumber(taskId);
        TaskExecution execution =
                taskExecutionRepository.save(TaskExecution.start(taskId, executionNumber, null, agentInput));
        final String executionId = execution.getId();

        taskAuditService.logStarted(taskId, executionId);

        final long startTime = System.currentTimeMillis();

        try {
            cancellationToken.checkCancelled();

            LOGGER.info("Starting task: {}", task.getName());
            conversationEnsurer.ensureExists(conversationId);

            eventBus.emit(AgentEvent.of(EventKind.LLM_REQUEST, meta, Map.of("prompt", agentInput)));

            cancellationToken.checkCancelled();

            final long llmStart = System.currentTimeMillis();
            final TaskResult result = agent.prompt(conversationId, agentInput, TaskResult.class);
            final long llmDuration = System.currentTimeMillis() - llmStart;

            eventBus.emit(AgentEvent.of(EventKind.LLM_RESPONSE, meta, Map.of("feedback", nullSafe(result.feedback()))));

            taskAuditService.logLlmCall(taskId, executionId, null, agentInput, result.feedback(), null, llmDuration);

            final Task completedTask = taskRepository.save(
                    inProgress.withFeedback(result.feedback()).withStatus(result.newStatus()));
            taskExecutionRepository.save(execution.withCompleted(result.feedback(), null, null));

            final long totalDuration = System.currentTimeMillis() - startTime;
            taskAuditService.logCompleted(taskId, executionId, totalDuration);

            eventBus.emit(AgentEvent.of(
                    EventKind.TASK_STATUS_CHANGE,
                    meta,
                    Map.of("status", result.newStatus().name())));

            deliverSafely(completedTask, buildCompletionMessage(task, result));

            LOGGER.info("Finished task: {} with status {}", task.getName(), result.newStatus());
        } catch (TaskCancelledException e) {
            final Task cancelledTask = taskRepository.save(inProgress.withStatus(Task.Status.cancelled));
            taskExecutionRepository.save(execution.withCancelled());
            taskAuditService.logCancelled(taskId, executionId);
            eventBus.emit(AgentEvent.of(EventKind.TASK_CANCELLED, meta));
            deliverSafely(cancelledTask, "Task '%s' was cancelled.".formatted(task.getName()));
            LOGGER.info("Task cancelled: {}", task.getName());
        } catch (Exception e) {
            final long totalDuration = System.currentTimeMillis() - startTime;
            final Task failedTask = taskRepository.save(inProgress.withStatus(Task.Status.failed));
            taskExecutionRepository.save(execution.withFailed(e.getMessage(), stackTraceToString(e)));
            taskAuditService.logFailed(taskId, executionId, e.getMessage(), stackTraceToString(e), totalDuration);
            eventBus.emit(AgentEvent.of(EventKind.ERROR, meta, Map.of("error", nullSafe(e.getMessage()))));
            deliverSafely(failedTask, "Task '%s' failed: %s".formatted(task.getName(), nullSafe(e.getMessage())));
            LOGGER.error("Task failed: {}", task.getName(), e);
            throw e;
        } finally {
            cancellationTokenRegistry.remove(taskId);
            eventBus.emit(AgentEvent.of(EventKind.TURN_END, meta));
        }
    }

    private int nextExecutionNumber(final String taskId) {
        final List<TaskExecution> existing = taskExecutionRepository.findByTaskIdOrderByExecutionNumberDesc(taskId);
        return existing.isEmpty() ? 1 : existing.getFirst().getExecutionNumber() + 1;
    }

    private static String stackTraceToString(final Exception e) {
        final StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static String nullSafe(final String value) {
        return value != null ? value : "";
    }

    private void deliverSafely(final Task task, final String message) {
        try {
            deliveryService.deliver(task, message);
        } catch (Exception e) {
            LOGGER.warn("Failed to deliver notification for task '{}': {}", task.getName(), e.getMessage());
        }
    }

    private String buildCompletionMessage(final Task task, final TaskResult result) {
        if (completed == result.newStatus()) {
            return "Task '%s' completed:\n%s".formatted(task.getName(), result.feedback());
        } else if (awaiting_human_input == result.newStatus()) {
            return "Task '%s' is waiting for your input:\n%s".formatted(task.getName(), result.feedback());
        }
        return "Task '%s' finished with status %s".formatted(task.getName(), result.newStatus());
    }

    private String formatTaskForAgent(final Task task) {
        return String.format(
                """
                Handle the following task and report the new status ('completed' or 'awaiting_human_input') with the feedback what was done
                Task '%s': %s
                """,
                task.getName(), task.getDescription());
    }

    public record TaskResult(Task.Status newStatus, String feedback) {}
}
