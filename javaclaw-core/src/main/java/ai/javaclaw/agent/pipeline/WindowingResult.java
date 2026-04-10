package ai.javaclaw.agent.pipeline;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

/**
 * Result of turn-boundary windowing, containing both retained and dropped messages.
 *
 * @param retained messages kept within the token budget (newest turns)
 * @param dropped  messages removed to fit the budget (oldest turns)
 */
public record WindowingResult(List<Message> retained, List<Message> dropped) {

    /** Returns {@code true} if any messages were dropped during windowing. */
    public boolean hasDroppedMessages() {
        return dropped != null && !dropped.isEmpty();
    }
}
