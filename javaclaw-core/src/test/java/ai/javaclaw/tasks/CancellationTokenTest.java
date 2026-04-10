package ai.javaclaw.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CancellationTokenTest {

    @Test
    void newTokenIsNotCancelled() {
        final CancellationToken token = new CancellationToken("task-1");

        assertThat(token.isCancelled()).isFalse();
        assertThat(token.getTaskId()).isEqualTo("task-1");
    }

    @Test
    void checkCancelledDoesNotThrowWhenNotCancelled() {
        final CancellationToken token = new CancellationToken("task-1");

        assertThatCode(token::checkCancelled).doesNotThrowAnyException();
    }

    @Test
    void cancelSetsFlag() {
        final CancellationToken token = new CancellationToken("task-1");

        token.cancel();

        assertThat(token.isCancelled()).isTrue();
    }

    @Test
    void checkCancelledThrowsAfterCancel() {
        final CancellationToken token = new CancellationToken("task-1");
        token.cancel();

        assertThatThrownBy(token::checkCancelled)
                .isInstanceOf(TaskCancelledException.class)
                .hasMessageContaining("task-1");
    }

    @Test
    void cancelIsIdempotent() {
        final CancellationToken token = new CancellationToken("task-1");

        token.cancel();
        token.cancel();

        assertThat(token.isCancelled()).isTrue();
    }

    @Test
    void cancelIsVisibleAcrossThreads() throws InterruptedException {
        final CancellationToken token = new CancellationToken("task-1");
        final AtomicBoolean seenCancelled = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        final Thread checker = new Thread(() -> {
            while (!token.isCancelled()) {
                Thread.onSpinWait();
            }
            seenCancelled.set(true);
            latch.countDown();
        });
        checker.start();

        token.cancel();
        latch.await();

        assertThat(seenCancelled.get()).isTrue();
    }

    @Test
    void taskCancelledExceptionContainsTaskId() {
        final TaskCancelledException ex = new TaskCancelledException("task-42");

        assertThat(ex.getTaskId()).isEqualTo("task-42");
        assertThat(ex.getMessage()).isEqualTo("Task 'task-42' was cancelled");
    }
}
