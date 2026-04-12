package ai.javaclaw.agent.memory.adapter.jdbc;

import ai.javaclaw.agent.memory.ChatMemory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ChatMemory} adapter backed by Spring Data JDBC.
 *
 * <p>All persistence goes through {@link ChatMemoryEntryJdbcRepository} — no Spring AI
 * JDBC memory starter is required. {@link #findConversationIds()} uses
 * {@link NamedParameterJdbcTemplate} directly because Spring Data JDBC does not reliably
 * support scalar {@code List<String>} projections via {@code @Query}.
 *
 * <p>Insertion order is preserved by the surrogate {@code id} PK
 * (PostgreSQL IDENTITY / SQLite AUTOINCREMENT), which {@code findByConversationId}
 * uses for {@code ORDER BY}. {@code created_at} is informational metadata only.
 */
@Primary
@Component
public class JdbcChatMemory implements ChatMemory {

    /** SQL returning the distinct set of conversation IDs that have at least one memory entry. */
    private static final String SELECT_DISTINCT_CONV_IDS = "SELECT DISTINCT conversation_id FROM spring_ai_chat_memory";

    /** Spring Data JDBC repository — the sole mechanism for chat-memory CRUD. */
    private final ChatMemoryEntryJdbcRepository repository;

    /** Used for scalar-list queries not expressible via @Query on the repository. */
    private final NamedParameterJdbcTemplate namedJdbc;

    public JdbcChatMemory(final ChatMemoryEntryJdbcRepository repository, final NamedParameterJdbcTemplate namedJdbc) {
        this.repository = repository;
        this.namedJdbc = namedJdbc;
    }

    @Override
    public List<String> findConversationIds() {
        return namedJdbc.queryForList(SELECT_DISTINCT_CONV_IDS, Map.of(), String.class);
    }

    @Override
    public List<Message> findByConversationId(final String conversationId) {
        return repository.findByConversationId(conversationId).stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    @Transactional
    public void appendAll(final String conversationId, final List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        final Instant now = Instant.now();
        final List<ChatMemoryEntry> entries = messages.stream()
                .map(m -> new ChatMemoryEntry(
                        null, conversationId, m.getText(), m.getMessageType().name(), now))
                .toList();
        repository.saveAll(entries);
    }

    @Override
    @Transactional
    public void saveAll(final String conversationId, final List<Message> messages) {
        repository.deleteByConversationId(conversationId);
        appendAll(conversationId, messages);
    }

    @Override
    public void deleteByConversationId(final String conversationId) {
        repository.deleteByConversationId(conversationId);
    }

    /**
     * Converts a persisted {@link ChatMemoryEntry} back to the appropriate {@link Message} subtype.
     *
     * <p>TOOL messages stored with {@code null} content are reconstructed as an empty
     * {@link ToolResponseMessage} (tool-call metadata is not persisted to the memory table).
     */
    private Message toMessage(final ChatMemoryEntry entry) {
        return switch (MessageType.valueOf(entry.type())) {
            case USER -> new UserMessage(entry.content());
            case ASSISTANT -> new AssistantMessage(entry.content());
            case SYSTEM -> new SystemMessage(entry.content());
            case TOOL -> ToolResponseMessage.builder().responses(List.of()).build();
        };
    }
}
