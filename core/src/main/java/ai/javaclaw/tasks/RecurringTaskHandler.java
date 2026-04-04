package ai.javaclaw.tasks;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RecurringTaskHandler {

    private static final Logger LOGGER =
            new JobRunrDashboardLogger(LoggerFactory.getLogger(RecurringTaskHandler.class));

    private final TaskManager taskManager;
    private final RecurringTaskRepository recurringTaskRepository;

    public RecurringTaskHandler(TaskManager taskManager, RecurringTaskRepository recurringTaskRepository) {
        this.taskManager = taskManager;
        this.recurringTaskRepository = recurringTaskRepository;
    }

    @Job(name = "Recurring task '%0'", retries = 3)
    public void executeTask(String recurringTaskId) {
        RecurringTask recurringTask = recurringTaskRepository
                .findById(recurringTaskId)
                .orElseThrow(() -> new TaskNotFoundException(recurringTaskId));
        taskManager.createTaskFromRecurringTask(recurringTask);
    }
}
