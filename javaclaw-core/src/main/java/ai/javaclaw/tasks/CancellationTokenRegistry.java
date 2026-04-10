package ai.javaclaw.tasks;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Registry of active CancellationTokens for in-progress tasks.
 * Per-pod (JVM-scoped): each pod tracks only its own running tasks.
 * TaskHandler registers a token on task start and removes it on completion.
 * TaskManager looks up the token when user requests cancellation (S7).
 */
@Component
public class CancellationTokenRegistry {

    private final ConcurrentMap<String, CancellationToken> tokens = new ConcurrentHashMap<>();

    /** Register a new token for a task. Returns the token for use by TaskHandler. */
    public CancellationToken register(final String taskId) {
        final CancellationToken token = new CancellationToken(taskId);
        tokens.put(taskId, token);
        return token;
    }

    /** Cancel a task's token if it exists on this pod. Returns true if the token was found and cancelled. */
    public boolean cancel(final String taskId) {
        final CancellationToken token = tokens.get(taskId);
        if (token != null) {
            token.cancel();
            return true;
        }
        return false;
    }

    /** Remove the token after task completion/failure/cancellation. */
    public void remove(final String taskId) {
        tokens.remove(taskId);
    }

    /** Look up a token. For testing and advanced use cases. */
    public Optional<CancellationToken> get(final String taskId) {
        return Optional.ofNullable(tokens.get(taskId));
    }

    /** Number of active tokens. For monitoring/testing. */
    public int activeCount() {
        return tokens.size();
    }
}
