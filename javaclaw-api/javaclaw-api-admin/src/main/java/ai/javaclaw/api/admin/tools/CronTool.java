package ai.javaclaw.api.admin.tools;

import ai.javaclaw.tasks.RecurringTask;
import ai.javaclaw.tasks.TaskManager;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Agent tool for managing recurring task schedules: validate cron expressions,
 * view details, update schedules, pause and resume recurring tasks.
 */
public class CronTool {

    private static final Logger logger = LoggerFactory.getLogger(CronTool.class);

    private final TaskManager taskManager;

    public CronTool(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Tool(
            description =
                    """
            Validates a cron expression and returns a human-readable description.
            Use this when a user wants to verify a cron expression before scheduling.

            - cronExpression: A standard quartz-style cron expression (e.g., '0 9 * * *' for daily at 9 AM).
              Do not use ? in a cron expression.
            """)
    public String validateCron(String cronExpression) {
        if (cronExpression == null || cronExpression.isBlank()) {
            return "Error: cron expression must not be blank.";
        }
        try {
            String description = describeCronExpression(cronExpression);
            return String.format("Valid cron expression '%s': %s", cronExpression, description);
        } catch (Exception e) {
            return String.format("Invalid cron expression '%s': %s", cronExpression, e.getMessage());
        }
    }

    @Tool(
            description =
                    """
            Shows detailed information about a specific recurring task by name,
            including its cron expression, active status, description, and creation time.

            - name: The name of the recurring task.
            """)
    public String getScheduleDetails(String name) {
        if (name == null || name.isBlank()) {
            return "Error: name must not be blank.";
        }
        try {
            RecurringTask task = taskManager.getRecurringTaskByName(name);
            StringBuilder sb = new StringBuilder();
            sb.append("Schedule details for '")
                    .append(task.getName())
                    .append("':")
                    .append('\n');
            sb.append("  ID: ").append(task.getId()).append('\n');
            sb.append("  Cron: ").append(task.getCronExpression()).append('\n');
            sb.append("  Description: ").append(task.getDescription()).append('\n');
            sb.append("  Active: ")
                    .append(task.isActive() ? "yes" : "no (paused)")
                    .append('\n');
            sb.append("  Created: ").append(task.getCreatedAt()).append('\n');
            if (task.getConversationId() != null) {
                sb.append("  Conversation: ").append(task.getConversationId()).append('\n');
            }
            try {
                sb.append("  Human-readable: ").append(describeCronExpression(task.getCronExpression()));
            } catch (Exception ignored) {
                // cron description is best-effort
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to get schedule details", e);
            return "Error: " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Updates the cron expression of an existing recurring task.
            The task is re-registered with JobRunr using the new schedule.
            If the task is paused, the new cron is saved but won't run until resumed.

            - name: The name of the recurring task to update.
            - newCronExpression: The new quartz-style cron expression.
            """)
    public String updateSchedule(String name, String newCronExpression) {
        if (name == null || name.isBlank()) {
            return "Error: name must not be blank.";
        }
        if (newCronExpression == null || newCronExpression.isBlank()) {
            return "Error: new cron expression must not be blank.";
        }
        try {
            taskManager.updateRecurringTaskCron(name, newCronExpression);
            return String.format("Schedule '%s' updated to cron expression '%s'.", name, newCronExpression);
        } catch (Exception e) {
            logger.error("Failed to update schedule", e);
            return "Error: " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Pauses a recurring task. The task remains in the database but stops executing.
            Use this when a user wants to temporarily stop a recurring task without deleting it.

            - name: The name of the recurring task to pause.
            """)
    public String pauseSchedule(String name) {
        if (name == null || name.isBlank()) {
            return "Error: name must not be blank.";
        }
        try {
            taskManager.pauseRecurringTask(name);
            return String.format("Recurring task '%s' has been paused.", name);
        } catch (Exception e) {
            logger.error("Failed to pause schedule", e);
            return "Error: " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Resumes a previously paused recurring task. The task is re-registered
            with JobRunr and will start executing according to its cron expression.

            - name: The name of the recurring task to resume.
            """)
    public String resumeSchedule(String name) {
        if (name == null || name.isBlank()) {
            return "Error: name must not be blank.";
        }
        try {
            taskManager.resumeRecurringTask(name);
            return String.format("Recurring task '%s' has been resumed.", name);
        } catch (Exception e) {
            logger.error("Failed to resume schedule", e);
            return "Error: " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Lists all recurring tasks with their status (active/paused), cron expression, and name.
            Shows more detail than the basic listRecurringTasks in TaskTool, including active status.
            """)
    public String listSchedules() {
        try {
            List<RecurringTask> tasks = taskManager.getAllRecurringTasks();
            if (tasks.isEmpty()) {
                return "No recurring tasks found.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Recurring schedules (").append(tasks.size()).append("):\n");
            for (RecurringTask task : tasks) {
                sb.append("- ").append(task.getName());
                sb.append(" [").append(task.isActive() ? "ACTIVE" : "PAUSED").append("]");
                sb.append(" cron='").append(task.getCronExpression()).append("'");
                if (task.getDescription() != null) {
                    sb.append(" — ")
                            .append(
                                    task.getDescription(),
                                    0,
                                    Math.min(task.getDescription().length(), 80));
                }
                sb.append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to list schedules", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Generates a human-readable description of a cron expression.
     * Basic implementation covering common patterns.
     */
    static String describeCronExpression(String cron) {
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("Cron expression must not be blank");
        }
        String[] parts = cron.trim().split("\\s+");
        if (parts.length < 5 || parts.length > 6) {
            throw new IllegalArgumentException("Invalid cron expression: expected 5 or 6 fields, got " + parts.length);
        }

        String minute = parts[0];
        String hour = parts[1];
        String dayOfMonth = parts[2];
        String month = parts[3];
        String dayOfWeek = parts[4];

        // Every minute
        if ("*".equals(minute)
                && "*".equals(hour)
                && "*".equals(dayOfMonth)
                && "*".equals(month)
                && "*".equals(dayOfWeek)) {
            return "every minute";
        }

        StringBuilder desc = new StringBuilder();

        // Time part
        if (!"*".equals(minute) && !"*".equals(hour)) {
            desc.append("at ").append(hour).append(":").append(String.format("%02d", Integer.parseInt(minute)));
        } else if ("0".equals(minute) && "*".equals(hour)) {
            desc.append("every hour at minute 0");
        } else if (!"*".equals(minute) && "*".equals(hour)) {
            desc.append("every hour at minute ").append(minute);
        } else if ("*".equals(minute) && !"*".equals(hour)) {
            desc.append("every minute during hour ").append(hour);
        }

        // Day of week
        if (!"*".equals(dayOfWeek)) {
            desc.append(" on ").append(describeDayOfWeek(dayOfWeek));
        }

        // Day of month
        if (!"*".equals(dayOfMonth)) {
            desc.append(" on day ").append(dayOfMonth).append(" of the month");
        }

        // Month
        if (!"*".equals(month)) {
            desc.append(" in month ").append(month);
        }

        // Fallback to daily if only hour:minute specified
        if ("*".equals(dayOfMonth)
                && "*".equals(month)
                && "*".equals(dayOfWeek)
                && !"*".equals(hour)
                && !"*".equals(minute)) {
            desc.append(" daily");
        }

        return desc.toString().trim();
    }

    private static String describeDayOfWeek(String dow) {
        return switch (dow.toUpperCase()) {
            case "0", "SUN" -> "Sunday";
            case "1", "MON" -> "Monday";
            case "2", "TUE" -> "Tuesday";
            case "3", "WED" -> "Wednesday";
            case "4", "THU" -> "Thursday";
            case "5", "FRI" -> "Friday";
            case "6", "SAT" -> "Saturday";
            default -> dow;
        };
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

        public CronTool build() {
            return new CronTool(taskManager);
        }
    }
}
