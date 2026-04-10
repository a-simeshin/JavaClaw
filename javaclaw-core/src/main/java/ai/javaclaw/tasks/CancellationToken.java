package ai.javaclaw.tasks;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe token for cooperative task cancellation.
 * TaskHandler checks this token before each step (LLM call, tool execution).
 * TaskManager sets it when user requests cancellation (S7).
 */
public class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final String taskId;

    public CancellationToken(final String taskId) {
        this.taskId = taskId;
    }

    /** Signal cancellation. Thread-safe, idempotent. */
    public void cancel() {
        cancelled.set(true);
    }

    /** Check if cancellation has been requested. */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Check cancellation and throw if cancelled.
     * Call this before each step in TaskHandler (LLM call, tool execution).
     *
     * @throws TaskCancelledException if cancellation was requested
     */
    public void checkCancelled() {
        if (cancelled.get()) {
            throw new TaskCancelledException(taskId);
        }
    }

    public String getTaskId() {
        return taskId;
    }
}
