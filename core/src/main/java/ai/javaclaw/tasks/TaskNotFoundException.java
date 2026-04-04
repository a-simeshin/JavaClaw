package ai.javaclaw.tasks;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(String id) {
        super("Task with id '" + id + "' was not found.");
    }

    public TaskNotFoundException(String id, Throwable cause) {
        super("Task with id '" + id + "' was not found.", cause);
    }
}
