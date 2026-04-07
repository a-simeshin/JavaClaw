package ai.javaclaw.tasks;

import static ai.javaclaw.tasks.Task.Status.awaiting_human_input;
import static ai.javaclaw.tasks.Task.Status.completed;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelContextService;
import ai.javaclaw.channels.ChannelRegistry;
import ai.javaclaw.channels.RoutingContext;
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
    private final ChannelRegistry channelRegistry;
    private final ChannelContextService channelContextService;

    public TaskHandler(
            final Agent agent,
            final TaskRepository taskRepository,
            final ChannelRegistry channelRegistry,
            final ChannelContextService channelContextService) {
        this.agent = agent;
        this.taskRepository = taskRepository;
        this.channelRegistry = channelRegistry;
        this.channelContextService = channelContextService;
    }

    @Job(name = "%0", retries = 3)
    public void executeTask(final String taskId) {
        final Task task = taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));

        if (!Task.Status.todo.equals(task.getStatus())) {
            throw new IllegalStateException("Cannot handle task '" + task.getName() + "' with status "
                    + task.getStatus() + ". Only tasks that have status todo can be run");
        }

        final Task inProgress = taskRepository.save(task.withStatus(Task.Status.in_progress));
        try {
            LOGGER.info("Starting task: {}", task.getName());
            final String agentInput = formatTaskForAgent(inProgress);
            final TaskResult result = agent.prompt(taskId, agentInput, TaskResult.class);
            taskRepository.save(inProgress.withFeedback(result.feedback()).withStatus(result.newStatus()));
            notifyUser(inProgress, result);
            LOGGER.info("Finished task: {} with status {}", task.getName(), result.newStatus());
        } catch (Exception e) {
            taskRepository.save(inProgress.withStatus(Task.Status.todo));
            throw e;
        }
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
