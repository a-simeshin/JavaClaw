package ai.javaclaw.agent.pipeline;

import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

/**
 * Stateless component that estimates the token count of {@link Message} objects using a
 * simple character-based heuristic.
 *
 * <p>The estimate formula is {@code ceil(text.length / 3.5)}, which approximates the average
 * bytes-per-token ratio for English/code content processed by most LLM tokenizers.
 * This is intentionally conservative — real token counts may differ slightly, but the
 * approximation is sufficient for windowing purposes.
 */
@Component
public class TokenEstimator {

    /** Approximate characters per token used in the estimation formula. */
    private static final double CHARS_PER_TOKEN = 3.5;

    /**
     * Estimates the token count for a single {@link Message}.
     *
     * <p>Returns {@code 0} if the message text is {@code null} or empty.
     *
     * @param message the message to estimate; must not be {@code null}
     * @return estimated token count, always &ge; 0
     */
    public int estimate(final Message message) {
        if (message == null) {
            return 0;
        }
        final String text = message.getText();
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    /**
     * Estimates the total token count for a list of {@link Message}s by summing individual
     * estimates.
     *
     * <p>Returns {@code 0} for a {@code null} or empty list.
     *
     * @param messages the messages to estimate; may be {@code null}
     * @return total estimated token count, always &ge; 0
     */
    public int estimate(final List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (final Message message : messages) {
            total += estimate(message);
        }
        return total;
    }
}
