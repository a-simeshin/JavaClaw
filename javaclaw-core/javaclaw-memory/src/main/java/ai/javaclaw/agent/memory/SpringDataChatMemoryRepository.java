package ai.javaclaw.agent.memory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
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
 * {@link AppendableChatMemoryRepository} implementation backed entirely by Spring Data JDBC.
 *
 * <p>Replaces {@code JdbcAppendableChatMemoryRepository} which delegated to Spring AI's
 * {@code JdbcChatMemoryRepository}. All persistence now goes through
 * {@link ChatMemoryEntryRepository} — no Spring AI JDBC starter required.
 *
 * <p>Monotonic ordering guarantee: {@link #appendAll(String, List)} assigns
 * {@code Instant.ofEpochSecond(base + i)} to each entry in the batch, where {@code base}
 * is the current epoch-second. This ensures stable {@code created_at} ordering even if
 * multiple messages share the same wall-clock second.
 *
 * <p>{@link #findConversationIds()} uses {@code NamedParameterJdbcTemplate} directly because
 * Spring Data JDBC does not reliably support scalar {@code List<String>} projections via
 * {@code @Query} as part of its public API.
 */
@Primary
@Component
public class SpringDataChatMemoryRepository implements AppendableChatMemoryRepository {

    /** SQL returning the distinct set of conversation IDs that have at least one memory entry. */
    private static final String SELECT_DISTINCT_CONV_IDS = "SELECT DISTINCT conversation_id FROM spring_ai_chat_memory";

    /** Spring Data JDBC repository — the sole mechanism for chat-memory CRUD. */
    private final ChatMemoryEntryRepository repository;

    /** Used for scalar-list queries not expressible via @Query on the repository. */
    private final NamedParameterJdbcTemplate namedJdbc;

    public SpringDataChatMemoryRepository(
            final ChatMemoryEntryRepository repository, final NamedParameterJdbcTemplate namedJdbc) {
        this.repository = repository;
        this.namedJdbc = namedJdbc;
    }

    /**
     * Appends {@code messages} to the stored history of {@code conversationId}.
     *
     * <p>Each message gets a monotonically increasing {@code created_at} (second-level granularity)
     * so that ordering within a single batch is deterministic.
     */
    @Override
    public void appendAll(final String conversationId, final List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        final long baseSecond = Instant.now().getEpochSecond();
        final List<ChatMemoryEntry> entries = IntStream.range(0, messages.size())
                .mapToObj(i -> {
                    final Message m = messages.get(i);
                    return new ChatMemoryEntry(
                            null,
                            conversationId,
                            m.getText(),
                            m.getMessageType().name(),
                            Instant.ofEpochSecond(baseSecond + i));
                })
                .toList();
        entries.forEach(repository::save);
    }

    /**
     * Replaces the entire history of {@code conversationId} with {@code messages}.
     *
     * <p>Runs delete + appendAll within a single transaction so partial failures cannot
     * leave the conversation in an inconsistent state.
     */
    @Override
    @Transactional
    public void saveAll(final String conversationId, final List<Message> messages) {
        repository.deleteByConversationId(conversationId);
        appendAll(conversationId, messages);
    }

    /** Returns all stored messages for {@code conversationId} in chronological order. */
    @Override
    public List<Message> findByConversationId(final String conversationId) {
        return repository.findByConversationId(conversationId).stream()
                .map(this::toMessage)
                .toList();
    }

    /** Removes all stored messages for {@code conversationId}. */
    @Override
    public void deleteByConversationId(final String conversationId) {
        repository.deleteByConversationId(conversationId);
    }

    /** Returns the distinct set of conversation IDs that have at least one stored message. */
    @Override
    public List<String> findConversationIds() {
        return namedJdbc.queryForList(SELECT_DISTINCT_CONV_IDS, Map.of(), String.class);
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
