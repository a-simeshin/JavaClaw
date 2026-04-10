package ai.javaclaw.tasks;

import static ai.javaclaw.tasks.Task.Status.awaiting_human_input;
import static ai.javaclaw.tasks.Task.Status.completed;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.agent.event.AgentEvent;
import ai.javaclaw.agent.event.EventBus;
import ai.javaclaw.agent.event.EventKind;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelContextService;
import ai.javaclaw.channels.ChannelRegistry;
import ai.javaclaw.channels.RoutingContext;
import ai.javaclaw.conversations.ConversationEnsurer;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final ChannelRegistry channelRegistry;
    private final ChannelContextService channelContextService;
    private final ConversationEnsurer conversationEnsurer;
    private final CancellationTokenRegistry cancellationTokenRegistry;
    private final EventBus eventBus;

    public TaskHandler(
            final Agent agent,
            final TaskRepository taskRepository,
            final TaskExecutionRepository taskExecutionRepository,
            final ChannelRegistry channelRegistry,
            final ChannelContextService channelContextService,
            final ConversationEnsurer conversationEnsurer,
            final CancellationTokenRegistry cancellationTokenRegistry,
            final EventBus eventBus) {
        this.agent = agent;
        this.taskRepository = taskRepository;
        this.taskExecutionRepository = taskExecutionRepository;
        this.channelRegistry = channelRegistry;
        this.channelContextService = channelContextService;
        this.conversationEnsurer = conversationEnsurer;
        this.cancellationTokenRegistry = cancellationTokenRegistry;
        this.eventBus = eventBus;
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

        try {
            cancellationToken.checkCancelled();

            LOGGER.info("Starting task: {}", task.getName());
            conversationEnsurer.ensureExists(conversationId);

            eventBus.emit(AgentEvent.of(EventKind.LLM_REQUEST, meta, Map.of("prompt", agentInput)));

            cancellationToken.checkCancelled();

            final TaskResult result = agent.prompt(conversationId, agentInput, TaskResult.class);

            eventBus.emit(AgentEvent.of(EventKind.LLM_RESPONSE, meta, Map.of("feedback", nullSafe(result.feedback()))));

            taskRepository.save(inProgress.withFeedback(result.feedback()).withStatus(result.newStatus()));
            taskExecutionRepository.save(execution.withCompleted(result.feedback(), null, null));

            eventBus.emit(AgentEvent.of(
                    EventKind.TASK_STATUS_CHANGE,
                    meta,
                    Map.of("status", result.newStatus().name())));

            notifyUser(inProgress, result);

            LOGGER.info("Finished task: {} with status {}", task.getName(), result.newStatus());
        } catch (TaskCancelledException e) {
            taskRepository.save(inProgress.withStatus(Task.Status.cancelled));
            taskExecutionRepository.save(execution.withCancelled());
            eventBus.emit(AgentEvent.of(EventKind.TASK_CANCELLED, meta));
            LOGGER.info("Task cancelled: {}", task.getName());
        } catch (Exception e) {
            taskRepository.save(inProgress.withStatus(Task.Status.failed));
            taskExecutionRepository.save(execution.withFailed(e.getMessage(), stackTraceToString(e)));
            eventBus.emit(AgentEvent.of(EventKind.ERROR, meta, Map.of("error", nullSafe(e.getMessage()))));
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

    private void notifyUser(final Task task, final TaskResult result) {
        try {
            final String message = buildNotificationMessage(task, result);
            if (message == null) {
                return;
            }

            final Optional<RoutingContext> ctxOpt = channelContextService.getContext(task.getConversationId());

            final RoutingContext routingContext;
            final Channel channel;

            if (ctxOpt.isPresent()) {
                routingContext = ctxOpt.get();
                channel = channelRegistry.getChannel(routingContext.channelName());
            } else {
                // Fallback: legacy sourceChannelName for tasks created before this migration
                final String sourceChannelName = task.getSourceChannelName();
                channel = channelRegistry.getChannel(sourceChannelName);
                routingContext = new RoutingContext(sourceChannelName != null ? sourceChannelName : "", Map.of());
            }

            if (channel != null) {
                channel.sendMessage(routingContext, message);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to notify user about task '{}': {}", task.getName(), e.getMessage());
        }
    }

    private String buildNotificationMessage(final Task task, final TaskResult result) {
        if (completed == result.newStatus()) {
            return "📋 Task '%s' completed:\n%s".formatted(task.getName(), result.feedback());
        } else if (awaiting_human_input == result.newStatus()) {
            return "📋 Task '%s' is waiting for your input:\n%s".formatted(task.getName(), result.feedback());
        }
        return null;
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
