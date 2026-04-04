package ai.javaclaw.tasks;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelRegistry;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static ai.javaclaw.tasks.Task.Status.awaiting_human_input;
import static ai.javaclaw.tasks.Task.Status.completed;
import static java.util.Optional.ofNullable;

@Component
public class TaskHandler {

    private static final Logger LOGGER = new JobRunrDashboardLogger(LoggerFactory.getLogger(TaskHandler.class));

    private final Agent agent;
    private final TaskRepository taskRepository;
    private final ChannelRegistry channelRegistry;

    public TaskHandler(Agent agent, TaskRepository taskRepository, ChannelRegistry channelRegistry) {
        this.agent = agent;
        this.taskRepository = taskRepository;
        this.channelRegistry = channelRegistry;
    }

    @Job(name = "%0", retries = 3)
    public void executeTask(String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        if (!Task.Status.todo.equals(task.getStatus())) {
            throw new IllegalStateException("Cannot handle task '" + task.getName() + "' with status " + task.getStatus() + ". Only tasks that have status todo can be run");
        }

        Task inProgress = taskRepository.save(task.withStatus(Task.Status.in_progress));
        try {
            LOGGER.info("Starting task: {}", task.getName());
            String agentInput = formatTaskForAgent(inProgress);
            TaskResult result = agent.prompt(taskId, agentInput, TaskResult.class);
            taskRepository.save(inProgress.withFeedback(result.feedback()).withStatus(result.newStatus()));
            notifyUser(inProgress, result);
            LOGGER.info("Finished task: {} with status {}", task.getName(), result.newStatus());
        } catch (Exception e) {
            taskRepository.save(inProgress.withStatus(Task.Status.todo));
            throw e;
        }
    }

    private void notifyUser(Task task, TaskResult result) {
        try {
            Channel channel = channelRegistry.getChannel(task.getSourceChannelName());
            ofNullable(channel).ifPresent(c -> {
                if (completed == result.newStatus) {
                    channel.sendMessage("📋 Task '%s' completed:\n%s".formatted(task.getName(), result.feedback()));
                } else if (awaiting_human_input == result.newStatus) {
                    channel.sendMessage("📋 Task '%s' is waiting for your input:\n%s".formatted(task.getName(), result.feedback()));
                }
            });
        } catch (Exception e) {
            LOGGER.warn("Failed to notify user about task '{}': {}", task.getName(), e.getMessage());
        }
    }

    private String formatTaskForAgent(Task task) {
        return String.format("""
                Handle the following task and report the new status ('completed' or 'awaiting_human_input') with the feedback what was done
                Task '%s': %s
                """, task.getName(), task.getDescription());
    }

    public record TaskResult(Task.Status newStatus, String feedback) {}
}
