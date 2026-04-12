package ai.javaclaw.agent.memory.adapter.jdbc;

import ai.javaclaw.agent.memory.ChatMemory;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ChatMemory} adapter backed by Spring Data JDBC.
 *
 * <p>All persistence goes through {@link ChatMemoryEntryJdbcRepository} — no Spring AI
 * JDBC memory starter is required. This class is a pure adapter: every method delegates
 * to the repository, including {@link #findConversationIds()} which is served by
 * {@link ChatMemoryEntryJdbcRepository#findDistinctConversationIds()}.
 *
 * <p>Insertion order is preserved by the surrogate {@code id} PK
 * (PostgreSQL IDENTITY / SQLite AUTOINCREMENT), which {@code findByConversationId}
 * uses for {@code ORDER BY}. {@code created_at} is informational metadata only.
 *
 * <p>This class is a plain POJO. Instances are produced by
 * {@code ai.javaclaw.agent.memory.autoconfigure.JavaClawMemoryAutoConfiguration}
 * as the default {@link ChatMemory} bean (via {@code @ConditionalOnMissingBean}) —
 * not by component scanning. Consumers override by declaring their own
 * {@link ChatMemory} bean.
 */
@AllArgsConstructor
public class JdbcChatMemory implements ChatMemory {

    /** Spring Data JDBC repository — the sole mechanism for chat-memory CRUD. */
    private final ChatMemoryEntryJdbcRepository repository;

    @Override
    public List<String> findConversationIds() {
        return repository.findDistinctConversationIds();
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
