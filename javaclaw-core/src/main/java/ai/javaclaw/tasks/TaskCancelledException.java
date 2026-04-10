package ai.javaclaw.tasks;

/**
 * Thrown when a task's CancellationToken is checked after cancellation was requested.
 * Caught by TaskHandler to transition task to cancelled status gracefully.
 */
public class TaskCancelledException extends RuntimeException {

    private final String taskId;

    public TaskCancelledException(final String taskId) {
        super("Task '%s' was cancelled".formatted(taskId));
        this.taskId = taskId;
    }

    public String getTaskId() {
        return taskId;
    }
}
