package ai.javaclaw.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskRateLimiterTest {

    private static final String USER_ID = "user-1";
    private static final long MAX_CONCURRENT = 10;
    private static final long MAX_PER_HOUR = 100;
    private static final long MAX_RECURRING = 20;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private RecurringTaskRepository recurringTaskRepository;

    private TaskRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new TaskRateLimiter(
                taskRepository, recurringTaskRepository, MAX_CONCURRENT, MAX_PER_HOUR, MAX_RECURRING);
    }

    /** T34: under all limits → passes without exception. */
    @Test
    void checkLimitPassesWhenUnderAllLimits() {
        when(taskRepository.countByUserIdAndStatus(eq(USER_ID), eq(Task.Status.in_progress)))
                .thenReturn(5L);
        when(taskRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(50L);

        assertThatCode(() -> rateLimiter.checkLimit(USER_ID)).doesNotThrowAnyException();
    }

    /** T35: at concurrent limit → throws RateLimitExceededException. */
    @Test
    void checkLimitThrowsWhenConcurrentLimitReached() {
        when(taskRepository.countByUserIdAndStatus(eq(USER_ID), eq(Task.Status.in_progress)))
                .thenReturn(10L);

        assertThatThrownBy(() -> rateLimiter.checkLimit(USER_ID))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    final RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getUserId()).isEqualTo(USER_ID);
                    assertThat(rle.getLimitType()).isEqualTo("max_concurrent_tasks");
                    assertThat(rle.getCurrentCount()).isEqualTo(10L);
                    assertThat(rle.getMaxAllowed()).isEqualTo(MAX_CONCURRENT);
                });
    }

    /** T36: at hourly limit → throws RateLimitExceededException. */
    @Test
    void checkLimitThrowsWhenHourlyLimitReached() {
        when(taskRepository.countByUserIdAndStatus(eq(USER_ID), eq(Task.Status.in_progress)))
                .thenReturn(0L);
        when(taskRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(100L);

        assertThatThrownBy(() -> rateLimiter.checkLimit(USER_ID))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    final RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getLimitType()).isEqualTo("max_tasks_per_hour");
                    assertThat(rle.getCurrentCount()).isEqualTo(100L);
                    assertThat(rle.getMaxAllowed()).isEqualTo(MAX_PER_HOUR);
                });
    }

    /** T37: at recurring limit → throws RateLimitExceededException. */
    @Test
    void checkRecurringLimitThrowsWhenRecurringLimitReached() {
        when(taskRepository.countByUserIdAndStatus(eq(USER_ID), eq(Task.Status.in_progress)))
                .thenReturn(0L);
        when(taskRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(0L);
        when(recurringTaskRepository.count()).thenReturn(20L);

        assertThatThrownBy(() -> rateLimiter.checkRecurringLimit(USER_ID))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    final RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getLimitType()).isEqualTo("max_recurring_tasks");
                    assertThat(rle.getCurrentCount()).isEqualTo(20L);
                    assertThat(rle.getMaxAllowed()).isEqualTo(MAX_RECURRING);
                });
    }

    @Test
    void checkRecurringLimitPassesWhenUnderAllLimits() {
        when(taskRepository.countByUserIdAndStatus(eq(USER_ID), eq(Task.Status.in_progress)))
                .thenReturn(0L);
        when(taskRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(0L);
        when(recurringTaskRepository.count()).thenReturn(19L);

        assertThatCode(() -> rateLimiter.checkRecurringLimit(USER_ID)).doesNotThrowAnyException();
    }

    @Test
    void concurrentLimitCheckedBeforeHourlyLimit() {
        // Both limits exceeded — concurrent should be checked first
        when(taskRepository.countByUserIdAndStatus(eq(USER_ID), eq(Task.Status.in_progress)))
                .thenReturn(10L);

        assertThatThrownBy(() -> rateLimiter.checkLimit(USER_ID))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    final RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getLimitType()).isEqualTo("max_concurrent_tasks");
                });
    }

    @Test
    void exceptionMessageContainsAllDetails() {
        when(taskRepository.countByUserIdAndStatus(eq(USER_ID), eq(Task.Status.in_progress)))
                .thenReturn(10L);

        assertThatThrownBy(() -> rateLimiter.checkLimit(USER_ID))
                .hasMessageContaining(USER_ID)
                .hasMessageContaining("max_concurrent_tasks")
                .hasMessageContaining("10")
                .hasMessageContaining("10");
    }

    @Test
    void gettersReturnConfiguredLimits() {
        assertThat(rateLimiter.getMaxConcurrentTasks()).isEqualTo(MAX_CONCURRENT);
        assertThat(rateLimiter.getMaxTasksPerHour()).isEqualTo(MAX_PER_HOUR);
        assertThat(rateLimiter.getMaxRecurringTasks()).isEqualTo(MAX_RECURRING);
    }
}
