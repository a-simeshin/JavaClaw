package ai.javaclaw.agent.pipeline;

/**
 * Token budget configuration for a single LLM call.
 *
 * <p>Captures the four token reservations that together define how much of the context window
 * is available for conversation history. Instances are immutable value objects; use
 * {@link #withSystemTokens(int)} to derive a new record with an updated system reservation.
 */
public record TokenBudget(
        /** Maximum total tokens the model context window supports. */
        int maxContextTokens,
        /** Tokens reserved for the model's response. */
        int reservedForResponse,
        /** Tokens reserved for tool definitions. */
        int reservedForTools,
        /** Tokens consumed by the system message (computed per request). */
        int reservedForSystem) {

    /**
     * Returns the tokens available for conversation history.
     *
     * <p>The result is always non-negative: if the sum of all reservations exceeds the context
     * window, {@code 0} is returned rather than a negative value.
     *
     * @return available token budget for history, &ge; 0
     */
    public int availableForHistory() {
        return Math.max(0, maxContextTokens - reservedForResponse - reservedForTools - reservedForSystem);
    }

    /**
     * Creates a budget with updated system reservation.
     *
     * <p>All other fields are copied from this record unchanged.
     *
     * @param systemTokens estimated token count of the system message
     * @return new {@link TokenBudget} with {@link #reservedForSystem()} set to {@code systemTokens}
     */
    public TokenBudget withSystemTokens(final int systemTokens) {
        return new TokenBudget(maxContextTokens, reservedForResponse, reservedForTools, systemTokens);
    }
}
