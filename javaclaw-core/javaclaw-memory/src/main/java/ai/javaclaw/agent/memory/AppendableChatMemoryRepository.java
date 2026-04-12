package ai.javaclaw.agent.memory;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

/**
 * Extension of {@link ChatMemoryRepository} that supports appending messages to an existing
 * conversation without overwriting prior history.
 *
 * <p>The base {@link ChatMemoryRepository#saveAll(String, List)} contract replaces the entire
 * conversation history. {@link #appendAll(String, List)} adds new messages to the tail of the
 * stored sequence, preserving all prior messages.
 */
public interface AppendableChatMemoryRepository extends ChatMemoryRepository {

    /**
     * Appends {@code messages} to the stored history of {@code conversationId}.
     *
     * <p>Unlike {@link #saveAll(String, List)}, existing messages are not removed.
     */
    void appendAll(String conversationId, List<Message> messages);
}
