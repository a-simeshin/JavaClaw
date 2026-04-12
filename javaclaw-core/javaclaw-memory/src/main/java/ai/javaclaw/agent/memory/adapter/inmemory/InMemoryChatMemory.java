package ai.javaclaw.agent.memory.adapter.inmemory;

import ai.javaclaw.agent.memory.ChatMemory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.Message;

/**
 * Thread-unsafe in-memory {@link ChatMemory} adapter intended for unit tests and
 * local dev profiles that do not need persistence.
 *
 * <p>Not wired as a Spring bean; instantiate directly in tests that need a
 * simple non-mock stand-in for {@link ChatMemory}.
 */
@SuppressWarnings("unused")
public class InMemoryChatMemory implements ChatMemory {

    private final Map<String, List<Message>> store = new LinkedHashMap<>();

    @Override
    public List<String> findConversationIds() {
        return List.copyOf(store.keySet());
    }

    @Override
    public List<Message> findByConversationId(final String conversationId) {
        return List.copyOf(store.getOrDefault(conversationId, List.of()));
    }

    @Override
    public void appendAll(final String conversationId, final List<Message> messages) {
        store.computeIfAbsent(conversationId, k -> new ArrayList<>()).addAll(messages);
    }

    @Override
    public void saveAll(final String conversationId, final List<Message> messages) {
        store.put(conversationId, new ArrayList<>(messages));
    }

    @Override
    public void deleteByConversationId(final String conversationId) {
        store.remove(conversationId);
    }
}
