package ai.javaclaw.agent.memory;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

/**
 * JavaClaw chat memory port — the single abstraction over conversation history.
 *
 * <p>This interface is the only contract callers depend on for reading, writing and
 * discovering conversation history. Implementations live under the
 * {@code ai.javaclaw.agent.memory.adapter} package (JDBC, in-memory, …) and are wired
 * by Spring as {@code @Component} beans — the JDBC adapter is marked {@code @Primary}
 * and picked up by default in production.
 *
 * <p>Ordering contract: {@link #findByConversationId(String)} returns messages in the
 * order they were appended. {@link #appendAll(String, List)} must preserve the relative
 * order of its argument and append to the tail of any existing history.
 *
 * <p>{@link #saveAll(String, List)} replaces the entire history for the conversation
 * atomically (implementations should run the delete and the append in a single
 * transaction where a transaction manager is available).
 */
public interface ChatMemory {

    /** Returns the distinct set of conversation ids that have at least one stored message. */
    List<String> findConversationIds();

    /** Returns all stored messages for {@code conversationId}, oldest first. */
    List<Message> findByConversationId(String conversationId);

    /** Appends {@code messages} to the tail of {@code conversationId}'s history. */
    void appendAll(String conversationId, List<Message> messages);

    /** Replaces the entire history of {@code conversationId} with {@code messages}. */
    void saveAll(String conversationId, List<Message> messages);

    /** Removes all stored messages for {@code conversationId}. */
    void deleteByConversationId(String conversationId);
}
