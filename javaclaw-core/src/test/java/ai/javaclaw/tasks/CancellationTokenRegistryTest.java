package ai.javaclaw.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CancellationTokenRegistryTest {

    private CancellationTokenRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CancellationTokenRegistry();
    }

    @Test
    void registerCreatesToken() {
        final CancellationToken token = registry.register("task-1");

        assertThat(token).isNotNull();
        assertThat(token.getTaskId()).isEqualTo("task-1");
        assertThat(token.isCancelled()).isFalse();
        assertThat(registry.activeCount()).isEqualTo(1);
    }

    @Test
    void cancelReturnsTrueAndCancelsToken() {
        final CancellationToken token = registry.register("task-1");

        final boolean result = registry.cancel("task-1");

        assertThat(result).isTrue();
        assertThat(token.isCancelled()).isTrue();
    }

    @Test
    void cancelReturnsFalseForUnknownTask() {
        final boolean result = registry.cancel("unknown-task");

        assertThat(result).isFalse();
    }

    @Test
    void removeDeletesToken() {
        registry.register("task-1");

        registry.remove("task-1");

        assertThat(registry.get("task-1")).isEmpty();
        assertThat(registry.activeCount()).isEqualTo(0);
    }

    @Test
    void getReturnsTokenIfPresent() {
        registry.register("task-1");

        assertThat(registry.get("task-1")).isPresent();
        assertThat(registry.get("unknown")).isEmpty();
    }

    @Test
    void multipleTasksTrackedIndependently() {
        final CancellationToken token1 = registry.register("task-1");
        final CancellationToken token2 = registry.register("task-2");

        registry.cancel("task-1");

        assertThat(token1.isCancelled()).isTrue();
        assertThat(token2.isCancelled()).isFalse();
        assertThat(registry.activeCount()).isEqualTo(2);
    }
}
