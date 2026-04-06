package ai.javaclaw.agent.pipeline;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token budget configuration for JavaClaw chat pipeline.
 *
 * <p>Bound from {@code application.yaml} under the {@code javaclaw.chat.token-budget} prefix.
 * Example configuration:
 *
 * <pre>{@code
 * javaclaw:
 *   chat:
 *     token-budget:
 *       max-context-tokens: 200000
 *       reserved-for-response: 8000
 *       reserved-for-tools: 4000
 * }</pre>
 */
@ConfigurationProperties(prefix = "javaclaw.chat.token-budget")
public class TokenBudgetProperties {

    /** Maximum context window tokens. Default: 200000 (Claude's 200k window). */
    private int maxContextTokens = 200_000;

    /** Tokens reserved for model response. Default: 8000. */
    private int reservedForResponse = 8_000;

    /** Tokens reserved for tool definitions. Default: 4000. */
    private int reservedForTools = 4_000;

    /**
     * Returns the maximum context window tokens.
     *
     * @return maximum context window tokens
     */
    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    /**
     * Sets the maximum context window tokens.
     *
     * @param maxContextTokens maximum context window tokens
     */
    public void setMaxContextTokens(final int maxContextTokens) {
        this.maxContextTokens = maxContextTokens;
    }

    /**
     * Returns the tokens reserved for the model response.
     *
     * @return reserved response tokens
     */
    public int getReservedForResponse() {
        return reservedForResponse;
    }

    /**
     * Sets the tokens reserved for the model response.
     *
     * @param reservedForResponse reserved response tokens
     */
    public void setReservedForResponse(final int reservedForResponse) {
        this.reservedForResponse = reservedForResponse;
    }

    /**
     * Returns the tokens reserved for tool definitions.
     *
     * @return reserved tool tokens
     */
    public int getReservedForTools() {
        return reservedForTools;
    }

    /**
     * Sets the tokens reserved for tool definitions.
     *
     * @param reservedForTools reserved tool tokens
     */
    public void setReservedForTools(final int reservedForTools) {
        this.reservedForTools = reservedForTools;
    }

    /**
     * Creates a {@link TokenBudget} from these properties without a system token reservation.
     *
     * <p>The system token count is computed per request and must be added via
     * {@link TokenBudget#withSystemTokens(int)} before use.
     *
     * @return new {@link TokenBudget} with {@code reservedForSystem = 0}
     */
    public TokenBudget toBudget() {
        return new TokenBudget(maxContextTokens, reservedForResponse, reservedForTools, 0);
    }
}
