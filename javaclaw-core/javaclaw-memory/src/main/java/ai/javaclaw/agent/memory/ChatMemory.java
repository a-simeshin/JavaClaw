package ai.javaclaw.agent.memory;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

/**
 * High-level chat memory abstraction. Replaces Spring AI's {@code ChatMemory}
 * to give JavaClaw full control over windowing, eviction, and persistence.
 */
public interface ChatMemory {
    void add(String conversationId, List<Message> messages);

    List<Message> get(String conversationId);

    void clear(String conversationId);
}
