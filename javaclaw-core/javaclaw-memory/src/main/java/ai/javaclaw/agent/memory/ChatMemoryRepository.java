package ai.javaclaw.agent.memory;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

/**
 * Repository for chat memory storage. Replaces Spring AI's {@code ChatMemoryRepository}
 * to give JavaClaw full control over the memory contract.
 */
public interface ChatMemoryRepository {
    List<String> findConversationIds();

    List<Message> findByConversationId(String conversationId);

    void saveAll(String conversationId, List<Message> messages);

    void deleteByConversationId(String conversationId);
}
