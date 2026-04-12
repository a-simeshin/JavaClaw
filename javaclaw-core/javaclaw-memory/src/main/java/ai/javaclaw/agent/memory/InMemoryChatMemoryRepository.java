package ai.javaclaw.agent.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.Message;

/**
 * Simple in-memory implementation of {@link AppendableChatMemoryRepository}.
 * Replaces Spring AI's {@code InMemoryChatMemoryRepository}.
 * Suitable for tests and builder defaults.
 */
public class InMemoryChatMemoryRepository implements AppendableChatMemoryRepository {

    private final Map<String, List<Message>> store = new LinkedHashMap<>();

    @Override
    public List<String> findConversationIds() {
        return List.copyOf(store.keySet());
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return List.copyOf(store.getOrDefault(conversationId, List.of()));
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        store.put(conversationId, new ArrayList<>(messages));
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        store.remove(conversationId);
    }

    @Override
    public void appendAll(String conversationId, List<Message> messages) {
        store.computeIfAbsent(conversationId, k -> new ArrayList<>()).addAll(messages);
    }
}
