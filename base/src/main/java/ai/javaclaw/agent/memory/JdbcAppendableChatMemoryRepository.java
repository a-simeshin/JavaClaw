package ai.javaclaw.agent.memory;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.memory.AppendableChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * JDBC-backed {@link AppendableChatMemoryRepository} that wraps Spring AI's
 * {@link JdbcChatMemoryRepository}.
 *
 * <p>{@link JdbcChatMemoryRepository} only supports full-replace via
 * {@code saveAll()}, so {@link #appendAll} is implemented as
 * read-existing + merge + save.
 */
@Component
@Primary
public class JdbcAppendableChatMemoryRepository implements AppendableChatMemoryRepository {

    private final JdbcChatMemoryRepository delegate;

    public JdbcAppendableChatMemoryRepository(JdbcChatMemoryRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public void appendAll(String conversationId, List<Message> messages) {
        List<Message> existing = delegate.findByConversationId(conversationId);
        List<Message> combined = new ArrayList<>(existing.size() + messages.size());
        combined.addAll(existing);
        combined.addAll(messages);
        delegate.saveAll(conversationId, combined);
    }

    @Override
    public List<String> findConversationIds() {
        return delegate.findConversationIds();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return delegate.findByConversationId(conversationId);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        delegate.saveAll(conversationId, messages);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        delegate.deleteByConversationId(conversationId);
    }
}
