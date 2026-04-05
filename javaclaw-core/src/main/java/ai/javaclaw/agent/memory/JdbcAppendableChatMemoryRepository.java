package ai.javaclaw.agent.memory;

import ai.javaclaw.ai.memory.AppendableChatMemoryRepository;
import java.util.List;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * JDBC-backed {@link AppendableChatMemoryRepository} that wraps Spring AI's
 * {@link JdbcChatMemoryRepository}.
 *
 * <p>{@link JdbcChatMemoryRepository} only supports full-replace via
 * {@code saveAll()}. That pattern wipes the DB-assigned {@code timestamp} of
 * every prior message on each append, collapsing them to the same value and
 * destroying per-message ordering. We instead do direct INSERTs for the new
 * messages so each row gets its own {@code CURRENT_TIMESTAMP} default.
 */
@Component
@Primary
public class JdbcAppendableChatMemoryRepository implements AppendableChatMemoryRepository {

    private static final String INSERT_SQL =
            "INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type) VALUES (?, ?, ?)";

    private final JdbcChatMemoryRepository delegate;
    private final JdbcTemplate jdbcTemplate;

    public JdbcAppendableChatMemoryRepository(JdbcChatMemoryRepository delegate, JdbcTemplate jdbcTemplate) {
        this.delegate = delegate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void appendAll(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, messages, 100, (ps, msg) -> {
            ps.setString(1, conversationId);
            ps.setString(2, msg.getText());
            ps.setString(3, msg.getMessageType().name());
        });
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
