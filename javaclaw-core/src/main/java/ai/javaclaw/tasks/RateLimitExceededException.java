package ai.javaclaw.tasks;

/**
 * Thrown when a user exceeds the configured task creation rate limits.
 * Caught by TaskManager to return a user-friendly error message.
 */
public class RateLimitExceededException extends RuntimeException {

    private final String userId;
    private final String limitType;
    private final long currentCount;
    private final long maxAllowed;

    public RateLimitExceededException(
            final String userId, final String limitType, final long currentCount, final long maxAllowed) {
        super("Rate limit exceeded for user '%s': %s (%d/%d)".formatted(userId, limitType, currentCount, maxAllowed));
        this.userId = userId;
        this.limitType = limitType;
        this.currentCount = currentCount;
        this.maxAllowed = maxAllowed;
    }

    public String getUserId() {
        return userId;
    }

    public String getLimitType() {
        return limitType;
    }

    public long getCurrentCount() {
        return currentCount;
    }

    public long getMaxAllowed() {
        return maxAllowed;
    }
}
