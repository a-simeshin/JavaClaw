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
 * <h2>Ordering guarantee</h2>
 * Insertion order is preserved by the surrogate {@code id} PK (PostgreSQL
 * {@code BIGINT GENERATED ALWAYS AS IDENTITY} / SQLite {@code INTEGER PRIMARY KEY
 * AUTOINCREMENT}), which {@code findByConversationId} uses for {@code ORDER BY}.
 * {@code created_at} is informational metadata only — SQLite has second-level
 * resolution, so two messages written in the same batch share a timestamp.
 *
 * <h2>Wiring</h2>
 * This class is a plain POJO. Instances are produced by
 * {@link ai.javaclaw.agent.memory.autoconfigure.JavaClawMemoryAutoConfiguration} as the
 * default {@link ChatMemory} bean (via {@code @ConditionalOnMissingBean}) — not by
 * component scanning. Consumers override by declaring their own {@link ChatMemory}
 * bean. The {@link lombok.AllArgsConstructor} generates the single-argument constructor
 * used by the autoconfiguration.
 *
 * <h2>Thread safety</h2>
 * Stateless and thread-safe: all state lives in the injected repository, whose
 * thread-safety is governed by Spring Data JDBC and the underlying {@code DataSource}.
 */
@AllArgsConstructor
public class JdbcChatMemory implements ChatMemory {

    /**
     * Spring Data JDBC repository — the sole mechanism for chat-memory CRUD.
     *
     * <p>Injected by {@link lombok.AllArgsConstructor}. Package-private access via the
     * field is intentionally absent; all access goes through the methods below so that
     * the transactional annotations apply.
     */
    private final ChatMemoryEntryJdbcRepository repository;

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link ChatMemoryEntryJdbcRepository#findDistinctConversationIds()},
     * which runs {@code SELECT DISTINCT conversation_id FROM spring_ai_chat_memory}
     * through a package-private {@code ConversationIdRowMapper}. Result order is
     * database-defined and not stable across calls.
     */
    @Override
    public List<String> findConversationIds() {
        return repository.findDistinctConversationIds();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads the ordered entries via
     * {@link ChatMemoryEntryJdbcRepository#findByConversationId(String)} (ordered by the
     * surrogate {@code id}) and rehydrates each row through {@link #toMessage} into the
     * concrete Spring AI {@link Message} subtype matching its stored {@code type}.
     */
    @Override
    public List<Message> findByConversationId(final String conversationId) {
        return repository.findByConversationId(conversationId).stream()
                .map(this::toMessage)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation notes:
     *
     * <ul>
     *   <li>{@code null} and empty lists short-circuit as a no-op, matching the
     *       {@link ChatMemory} contract.
     *   <li>All entries in the batch share a single {@link Instant#now()} value. This is
     *       harmless because ordering is resolved by the surrogate {@code id}, not by
     *       {@code created_at}.
     *   <li>Persistence goes through {@link ChatMemoryEntryJdbcRepository#saveAll(Iterable)},
     *       which Spring Data JDBC executes as a single SQL batch insert.
     *   <li>Wrapped in {@link Transactional} so a partial batch failure rolls back — no
     *       half-written history.
     * </ul>
     */
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

    /**
     * {@inheritDoc}
     *
     * <p><strong>⚠ Destructive — do not call from production code.</strong> Deletes every
     * message for {@code conversationId} and then re-appends {@code messages} inside a
     * single {@link Transactional} scope. An empty list is equivalent to calling
     * {@link #deleteByConversationId(String)}. This override exists purely to honour the
     * {@link ChatMemory} contract so adapter-level integration tests can assert the
     * replace-behaviour; use {@link #appendAll(String, List)} for normal writes.
     *
     * <p>The {@link Transactional} annotation guarantees atomicity: if the append leg
     * throws, the delete rolls back. It does <em>not</em>, however, protect against
     * semantic loss — any message not present in {@code messages} is gone for good the
     * moment the transaction commits.
     *
     * @deprecated see {@link ChatMemory#saveAll(String, List)}
     */
    @Override
    @Deprecated
    @Transactional
    @SuppressWarnings("DeprecatedIsStillUsed")
    public void saveAll(final String conversationId, final List<Message> messages) {
        repository.deleteByConversationId(conversationId);
        appendAll(conversationId, messages);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link ChatMemoryEntryJdbcRepository#deleteByConversationId(String)},
     * which fires a single {@code DELETE} statement. Spring Data JDBC runs this inside
     * its own auto-transaction, so no explicit {@link Transactional} is required.
     */
    @Override
    public void deleteByConversationId(final String conversationId) {
        repository.deleteByConversationId(conversationId);
    }

    /**
     * Converts a persisted {@link ChatMemoryEntry} back to the appropriate {@link Message} subtype.
     *
     * <p>The mapping is driven by {@link ChatMemoryEntry#type()} which stores
     * {@link MessageType#name()}. {@code TOOL} messages stored with {@code null} content
     * are reconstructed as an empty {@link ToolResponseMessage} (the memory table does
     * not persist tool-call metadata, so fidelity is limited to the message type).
     *
     * @param entry the row loaded from {@code spring_ai_chat_memory}
     * @return the Spring AI {@link Message} matching the stored type
     * @throws IllegalArgumentException if {@code entry.type()} is not a known
     *     {@link MessageType} name (indicates on-disk corruption)
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
