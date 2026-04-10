package ai.javaclaw.tasks;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-user rate limiter for task creation. Checks concurrent, hourly, and recurring limits
 * before allowing task creation/scheduling/spawning.
 *
 * <p>Called by TaskManager before create/schedule/spawn operations.
 */
@Component
public class TaskRateLimiter {

    private final TaskRepository taskRepository;
    private final RecurringTaskRepository recurringTaskRepository;

    private final long maxConcurrentTasks;
    private final long maxTasksPerHour;
    private final long maxRecurringTasks;

    public TaskRateLimiter(
            final TaskRepository taskRepository,
            final RecurringTaskRepository recurringTaskRepository,
            @Value("${javaclaw.tasks.rate-limit.max-concurrent:10}") final long maxConcurrentTasks,
            @Value("${javaclaw.tasks.rate-limit.max-per-hour:100}") final long maxTasksPerHour,
            @Value("${javaclaw.tasks.rate-limit.max-recurring:20}") final long maxRecurringTasks) {
        this.taskRepository = taskRepository;
        this.recurringTaskRepository = recurringTaskRepository;
        this.maxConcurrentTasks = maxConcurrentTasks;
        this.maxTasksPerHour = maxTasksPerHour;
        this.maxRecurringTasks = maxRecurringTasks;
    }

    /**
     * Checks all applicable rate limits for the given user before task creation.
     *
     * @throws RateLimitExceededException if any limit is exceeded
     */
    public void checkLimit(final String userId) {
        checkConcurrentLimit(userId);
        checkHourlyLimit(userId);
    }

    /**
     * Checks rate limits applicable to recurring task creation.
     *
     * @throws RateLimitExceededException if recurring task limit is exceeded
     */
    public void checkRecurringLimit(final String userId) {
        checkLimit(userId);
        long currentRecurring = recurringTaskRepository.count();
        if (currentRecurring >= maxRecurringTasks) {
            throw new RateLimitExceededException(userId, "max_recurring_tasks", currentRecurring, maxRecurringTasks);
        }
    }

    private void checkConcurrentLimit(final String userId) {
        long concurrent = taskRepository.countByUserIdAndStatus(userId, Task.Status.in_progress);
        if (concurrent >= maxConcurrentTasks) {
            throw new RateLimitExceededException(userId, "max_concurrent_tasks", concurrent, maxConcurrentTasks);
        }
    }

    private void checkHourlyLimit(final String userId) {
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long hourlyCount = taskRepository.countByUserIdAndCreatedAtAfter(userId, oneHourAgo);
        if (hourlyCount >= maxTasksPerHour) {
            throw new RateLimitExceededException(userId, "max_tasks_per_hour", hourlyCount, maxTasksPerHour);
        }
    }

    public long getMaxConcurrentTasks() {
        return maxConcurrentTasks;
    }

    public long getMaxTasksPerHour() {
        return maxTasksPerHour;
    }

    public long getMaxRecurringTasks() {
        return maxRecurringTasks;
    }
}
