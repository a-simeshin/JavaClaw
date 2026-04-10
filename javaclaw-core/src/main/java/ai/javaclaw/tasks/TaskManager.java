package ai.javaclaw.tasks;

import ai.javaclaw.agent.audit.TaskAuditService;
import ai.javaclaw.agent.event.AgentEvent;
import ai.javaclaw.agent.event.EventBus;
import ai.javaclaw.agent.event.EventKind;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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
    private static final int MAX_TASK_DEPTH = 3;

    private final JobScheduler jobScheduler;
    private final StorageProvider storageProvider;
    private final TaskRepository taskRepository;
    private final RecurringTaskRepository recurringTaskRepository;
    private final CancellationTokenRegistry cancellationTokenRegistry;
    private final EventBus eventBus;
    private final TaskAuditService taskAuditService;

    public TaskManager(
            JobScheduler jobScheduler,
            StorageProvider storageProvider,
            TaskRepository taskRepository,
            RecurringTaskRepository recurringTaskRepository,
            CancellationTokenRegistry cancellationTokenRegistry,
            EventBus eventBus,
            TaskAuditService taskAuditService) {
        this.jobScheduler = jobScheduler;
        this.storageProvider = storageProvider;
        this.taskRepository = taskRepository;
        this.recurringTaskRepository = recurringTaskRepository;
        this.cancellationTokenRegistry = cancellationTokenRegistry;
        this.eventBus = eventBus;
        this.taskAuditService = taskAuditService;
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

    /** Планирует повторяющуюся задачу без привязки к conversation. */
    public void scheduleRecurrently(final String cronExpression, final String name, final String description) {
        scheduleRecurrently(cronExpression, name, description, null);
    }

    /** Планирует повторяющуюся задачу с привязкой к conversation. */
    public void scheduleRecurrently(
            final String cronExpression, final String name, final String description, final String conversationId) {
        final RecurringTask recurringTask = recurringTaskRepository.save(
                RecurringTask.newRecurringTask(name, description, cronExpression, conversationId));
        jobScheduler.<RecurringTaskHandler>scheduleRecurrently(
                recurringTask.getName(), cronExpression, x -> x.executeTask(recurringTask.getId()));
        log.info(
                "Task '{}' ({}) has been scheduled recurrently with cronExpression {}.",
                name,
                recurringTask.getId(),
                cronExpression);
    }

    /**
     * Spawns a child task under the given parent. Inherits conversationId and userId from parent.
     * Depth limit: max {@value MAX_TASK_DEPTH} levels (parent → child → grandchild).
     */
    public Task spawn(final String parentTaskId, final String name, final String description) {
        final Task parent = taskRepository
                .findById(parentTaskId)
                .orElseThrow(() -> new IllegalArgumentException("Parent task not found: " + parentTaskId));

        int depth = calculateDepth(parent);
        if (depth >= MAX_TASK_DEPTH) {
            throw new IllegalStateException("Maximum task depth of " + MAX_TASK_DEPTH
                    + " exceeded. Cannot spawn child for task: " + parentTaskId);
        }

        final Task child = taskRepository.save(Task.newTask(name, description)
                .withParentTaskId(parentTaskId)
                .withConversationId(parent.getConversationId())
                .withUserId(parent.getUserId())
                .withRuntimeType(TaskRuntime.async));

        jobScheduler.<TaskHandler>enqueue(x -> x.executeTask(child.getId()));

        final AgentEvent.EventMeta meta = AgentEvent.EventMeta.ofTask(null, child.getId());
        eventBus.emit(AgentEvent.of(
                EventKind.SUBTASK_SPAWN,
                meta,
                Map.of("parentTaskId", parentTaskId, "childTaskId", child.getId(), "childName", name)));
        taskAuditService.logCreated(child.getId());

        log.info(
                "Child task '{}' ({}) spawned under parent '{}' at depth {}.",
                name,
                child.getId(),
                parentTaskId,
                depth + 1);
        return child;
    }

    /** Cancels a task: sets status to cancelled, cancels CancellationToken, emits event. */
    public void cancel(final String taskId) {
        final Task task = taskRepository
                .findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (task.getStatus() == Task.Status.completed
                || task.getStatus() == Task.Status.failed
                || task.getStatus() == Task.Status.cancelled) {
            log.info("Task '{}' ({}) is already in terminal state: {}", task.getName(), taskId, task.getStatus());
            return;
        }

        taskRepository.save(task.withStatus(Task.Status.cancelled));
        cancellationTokenRegistry.cancel(taskId);

        final AgentEvent.EventMeta cancelMeta = AgentEvent.EventMeta.ofTask(null, taskId);
        eventBus.emit(AgentEvent.of(
                EventKind.TASK_CANCELLED,
                cancelMeta,
                Map.of(
                        "taskName",
                        task.getName(),
                        "previousStatus",
                        task.getStatus().name())));
        taskAuditService.logCancelled(taskId, null);

        log.info("Task '{}' ({}) has been cancelled.", task.getName(), taskId);
    }

    /**
     * Calculates the depth of a task in the hierarchy (0 = root, 1 = child, 2 = grandchild).
     */
    int calculateDepth(final Task task) {
        int depth = 0;
        String currentParentId = task.getParentTaskId();
        while (currentParentId != null && depth < MAX_TASK_DEPTH + 1) {
            depth++;
            final String pid = currentParentId;
            currentParentId =
                    taskRepository.findById(pid).map(Task::getParentTaskId).orElse(null);
        }
        return depth;
    }

    /** Returns child tasks of the given parent. */
    public List<Task> getChildTasks(final String parentTaskId) {
        return taskRepository.findByParentTaskId(parentTaskId);
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

    /** Создаёт одиночный task из recurring task, наследуя conversationId. */
    public void createTaskFromRecurringTask(final RecurringTask recurringTask) {
        Task task = taskRepository.save(Task.newTask(recurringTask.getName(), recurringTask.getDescription())
                .withConversationId(recurringTask.getConversationId()));
        jobScheduler.<TaskHandler>enqueue(x -> x.executeTask(task.getId()));
        log.info("Task '{}' ({}) has been created from recurring task.", task.getName(), task.getId());
    }
}
