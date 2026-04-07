package ai.javaclaw.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.Paging;
import org.jobrunr.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TaskManager {

    private static final Logger log = LoggerFactory.getLogger(TaskManager.class);
    private final JobScheduler jobScheduler;
    private final StorageProvider storageProvider;
    private final TaskRepository taskRepository;
    private final RecurringTaskRepository recurringTaskRepository;

    public TaskManager(
            JobScheduler jobScheduler,
            StorageProvider storageProvider,
            TaskRepository taskRepository,
            RecurringTaskRepository recurringTaskRepository) {
        this.jobScheduler = jobScheduler;
        this.storageProvider = storageProvider;
        this.taskRepository = taskRepository;
        this.recurringTaskRepository = recurringTaskRepository;
    }

    public void create(final String name, final String description) {
        create(name, description, null);
    }

    public void create(final String name, final String description, final String conversationId) {
        final Task task = taskRepository.save(Task.newTask(name, description).withConversationId(conversationId));
        jobScheduler.<TaskHandler>enqueue(x -> x.executeTask(task.getId()));
        log.info("Task '{}' ({}) has been created.", task.getName(), task.getId());
    }

    public void schedule(final LocalDateTime executionTime, final String name, final String description) {
        schedule(executionTime, name, description, null);
    }

    public void schedule(
            final LocalDateTime executionTime,
            final String name,
            final String description,
            final String conversationId) {
        final Instant createdAt = executionTime.atZone(ZoneId.systemDefault()).toInstant();
        final Task task =
                taskRepository.save(Task.newTask(name, createdAt, description).withConversationId(conversationId));
        jobScheduler.<TaskHandler>schedule(executionTime, x -> x.executeTask(task.getId()));
        log.info("Task '{}' ({}) has been scheduled at {}.", task.getName(), task.getId(), executionTime);
    }

    public void scheduleRecurrently(String cronExpression, String name, String description) {
        RecurringTask recurringTask =
                recurringTaskRepository.save(RecurringTask.newRecurringTask(name, description, cronExpression));
        jobScheduler.<RecurringTaskHandler>scheduleRecurrently(
                recurringTask.getName(), cronExpression, x -> x.executeTask(recurringTask.getId()));
        log.info(
                "Task '{}' ({}) has been scheduled recurrently with cronExpression {}.",
                name,
                recurringTask.getId(),
                cronExpression);
    }

    public void deleteRecurringTask(String name) {
        RecurringTask recurringTask = recurringTaskRepository.findAll().stream()
                .filter(x -> x.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Recurring task with name " + name + " was not found"));
        jobScheduler.deleteRecurringJob(recurringTask.getName());
        List<Job> jobList =
                storageProvider.getJobList(StateName.SCHEDULED, Paging.AmountBasedList.ascOnUpdatedAt(1000));
        jobList.stream()
                .filter(j -> j.getRecurringJobId()
                        .map(recurringTask.getName()::equals)
                        .orElse(false))
                .map(Job::getId)
                .findFirst()
                .ifPresent(jobScheduler::delete);
        recurringTaskRepository.deleteById(recurringTask.getId());
        log.info("Recurring task '{}' ({}) has been deleted.", name, recurringTask.getId());
    }

    public List<RecurringTask> getAllRecurringTasks() {
        return recurringTaskRepository.findAll();
    }

    public List<Task> getTasks(LocalDate date, Task.Status status) {
        ZoneId zone = ZoneId.systemDefault();
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        if (status != null) {
            return taskRepository.findByCreatedAtBetweenAndStatus(from, to, status);
        }
        return taskRepository.findByCreatedAtBetween(from, to);
    }

    public void createTaskFromRecurringTask(RecurringTask recurringTask) {
        Task task = taskRepository.save(Task.newTask(recurringTask.getName(), recurringTask.getDescription()));
        jobScheduler.<TaskHandler>enqueue(x -> x.executeTask(task.getId()));
        log.info("Task '{}' ({}) has been created from recurring task.", task.getName(), task.getId());
    }
}
