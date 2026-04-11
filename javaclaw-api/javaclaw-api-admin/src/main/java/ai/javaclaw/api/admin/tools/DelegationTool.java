package ai.javaclaw.api.admin.tools;

import ai.javaclaw.tasks.Task;
import ai.javaclaw.tasks.TaskManager;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Agent tool for sub-agent delegation: spawn child tasks, check their status,
 * list children of a parent task, and cancel delegated work.
 */
public class DelegationTool {

    private static final Logger logger = LoggerFactory.getLogger(DelegationTool.class);

    private final TaskManager taskManager;

    public DelegationTool(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Tool(
            description =
                    """
            Delegates a sub-task to another agent instance. The child task runs asynchronously
            and inherits the parent's conversation and user context. Use this when a task can be
            broken into independent sub-tasks that can run in parallel.

            Maximum nesting depth is 3 levels.

            - parentTaskId: The ID of the current (parent) task that is delegating work.
            - name: Short descriptive name for the sub-task (e.g., 'research-competitors').
            - description: Detailed instructions for the sub-agent explaining what to accomplish.
            """)
    public String delegateTask(String parentTaskId, String name, String description) {
        if (parentTaskId == null || parentTaskId.isBlank()) {
            return "Error: parentTaskId must not be blank.";
        }
        if (name == null || name.isBlank()) {
            return "Error: name must not be blank.";
        }
        if (description == null || description.isBlank()) {
            return "Error: description must not be blank.";
        }
        try {
            Task child = taskManager.spawn(parentTaskId, name, description);
            return String.format(
                    "Sub-task '%s' (id: %s) has been delegated successfully. "
                            + "It will run asynchronously. Use checkDelegatedTask with this ID to monitor progress.",
                    child.getName(), child.getId());
        } catch (IllegalArgumentException e) {
            logger.warn("Delegation failed — invalid argument: {}", e.getMessage());
            return "Error: " + e.getMessage();
        } catch (IllegalStateException e) {
            logger.warn("Delegation failed — depth exceeded: {}", e.getMessage());
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Failed to delegate task", e);
            return "Error: Could not delegate task. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Checks the current status of a delegated sub-task by its ID.
            Returns the task name, status, and feedback (result) if completed.

            - taskId: The ID of the delegated sub-task to check.
            """)
    public String checkDelegatedTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return "Error: taskId must not be blank.";
        }
        try {
            Task task = taskManager.getTask(taskId);
            StringBuilder sb = new StringBuilder();
            sb.append("Task '")
                    .append(task.getName())
                    .append("' (")
                    .append(task.getId())
                    .append("):\n");
            sb.append("  Status: ").append(task.getStatus().name()).append("\n");
            if (task.getDescription() != null && !task.getDescription().isBlank()) {
                sb.append("  Description: ")
                        .append(
                                task.getDescription(),
                                0,
                                Math.min(task.getDescription().length(), 200))
                        .append("\n");
            }
            if (task.getFeedback() != null && !task.getFeedback().isBlank()) {
                sb.append("  Result: ")
                        .append(
                                task.getFeedback(),
                                0,
                                Math.min(task.getFeedback().length(), 500))
                        .append("\n");
            }
            if (task.getParentTaskId() != null) {
                sb.append("  Parent: ").append(task.getParentTaskId()).append("\n");
            }
            return sb.toString();
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Failed to check delegated task", e);
            return "Error: Could not check task status. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Lists all child sub-tasks of a given parent task.
            Use this to see the status of all delegated work from a parent task.

            - parentTaskId: The ID of the parent task whose children to list.
            """)
    public String listDelegatedTasks(String parentTaskId) {
        if (parentTaskId == null || parentTaskId.isBlank()) {
            return "Error: parentTaskId must not be blank.";
        }
        try {
            List<Task> children = taskManager.getChildTasks(parentTaskId);
            if (children.isEmpty()) {
                return "No delegated sub-tasks found for parent task: " + parentTaskId;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Delegated sub-tasks (").append(children.size()).append("):\n");
            for (Task child : children) {
                sb.append("- '")
                        .append(child.getName())
                        .append("' (")
                        .append(child.getId())
                        .append("): ")
                        .append(child.getStatus().name());
                if (child.getFeedback() != null && !child.getFeedback().isBlank()) {
                    String preview = child.getFeedback().length() > 100
                            ? child.getFeedback().substring(0, 100) + "..."
                            : child.getFeedback();
                    sb.append(" — ").append(preview);
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to list delegated tasks", e);
            return "Error: Could not list delegated tasks. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Cancels a delegated sub-task that is no longer needed.
            Only non-terminal tasks (not completed/failed/cancelled) can be cancelled.

            - taskId: The ID of the delegated sub-task to cancel.
            """)
    public String cancelDelegatedTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return "Error: taskId must not be blank.";
        }
        try {
            taskManager.cancel(taskId);
            return String.format("Delegated task '%s' has been cancelled.", taskId);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Failed to cancel delegated task", e);
            return "Error: Could not cancel task. " + e.getMessage();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private TaskManager taskManager;

        public Builder taskManager(TaskManager taskManager) {
            this.taskManager = taskManager;
            return this;
        }

        public DelegationTool build() {
            if (taskManager == null) {
                throw new IllegalStateException("TaskManager is required");
            }
            return new DelegationTool(taskManager);
        }
    }
}
