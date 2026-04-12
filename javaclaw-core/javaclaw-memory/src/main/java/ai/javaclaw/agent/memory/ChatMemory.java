package ai.javaclaw.agent.memory;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

/**
 * JavaClaw chat memory port — the single abstraction over conversation history.
 *
 * <p>This interface is the only contract callers depend on for reading, writing and
 * discovering conversation history. Implementations live under the
 * {@code ai.javaclaw.agent.memory.adapter} package:
 *
 * <ul>
 *   <li>{@code adapter.jdbc.JdbcChatMemory} — production-grade, Spring Data JDBC backed,
 *       works against PostgreSQL and SQLite via the Flyway schema under
 *       {@code db/migration/{postgresql|sqlite}}. Registered as the default bean by
 *       {@link ai.javaclaw.agent.memory.autoconfigure.JavaClawMemoryAutoConfiguration}
 *       through {@code @ConditionalOnMissingBean(ChatMemory.class)}.
 *   <li>{@code adapter.inmemory.InMemoryChatMemory} — thread-unsafe in-process fallback
 *       for unit tests and local dev; never wired as a Spring bean.
 * </ul>
 *
 * <p>Consumers override the default by declaring their own {@code ChatMemory} bean —
 * the autoconfiguration steps aside without any need for {@code @Primary}.
 *
 * <h2>Ordering contract</h2>
 * {@link #findByConversationId(String)} returns messages in the order they were appended.
 * {@link #appendAll(String, List)} must preserve the relative order of its argument and
 * append to the tail of any existing history. The JDBC adapter guarantees this via the
 * strictly monotonic surrogate {@code id} column, not {@code created_at}, which only has
 * second-level resolution on SQLite.
 *
 * <h2>Transactional semantics</h2>
 * Mutating methods are free to span a single database transaction. The JDBC adapter
 * annotates {@link #appendAll(String, List)} and {@link #saveAll(String, List)} with
 * {@code @Transactional}; {@link #deleteByConversationId(String)} is a single-statement
 * operation and does not declare an explicit transaction.
 *
 * <h2>Null and empty-list handling</h2>
 * Implementations must treat {@code appendAll(id, null)} and {@code appendAll(id, List.of())}
 * as no-ops. Conversation ids are assumed non-null; callers that pass {@code null} will
 * see implementation-specific failures (NPE from Spring JDBC parameter binding).
 */
@SuppressWarnings("DeprecatedIsStillUsed")
public interface ChatMemory {

    /**
     * Returns the distinct set of conversation ids that have at least one stored message.
     *
     * <p>Conversations whose messages have all been deleted are not included. Result order
     * is implementation-defined — callers that need deterministic ordering must sort the
     * returned list themselves.
     *
     * @return a list of conversation ids, possibly empty, never {@code null}
     */
    List<String> findConversationIds();

    /**
     * Returns all stored messages for {@code conversationId}, oldest first.
     *
     * <p>Ordering is by insertion order. The JDBC adapter enforces this via the surrogate
     * {@code id} PK rather than {@code created_at}, because multiple messages written in
     * the same batch share the same wall-clock timestamp.
     *
     * @param conversationId the conversation to load; must be non-null
     * @return messages in insertion order, possibly empty, never {@code null}
     */
    List<Message> findByConversationId(String conversationId);

    /**
     * Appends {@code messages} to the tail of {@code conversationId}'s history.
     *
     * <p>This is the primary write path for chat memory. Use it whenever you want to
     * accumulate new turns of the conversation without touching prior history.
     *
     * <p>Contract:
     *
     * <ul>
     *   <li>{@code messages == null} → no-op.
     *   <li>{@code messages.isEmpty()} → no-op.
     *   <li>Non-empty → all entries are persisted in a single batch, preserving the
     *       relative order of the argument list.
     * </ul>
     *
     * @param conversationId the conversation to append to; must be non-null
     * @param messages the messages to append; may be {@code null} or empty (no-op)
     */
    void appendAll(String conversationId, List<Message> messages);

    /**
     * Replaces the entire history of {@code conversationId} with {@code messages}.
     *
     * <p><strong>⚠ Do not use in production code.</strong> This method exists only to
     * satisfy the {@link ChatMemory} port contract and to keep adapter implementations
     * symmetric with alternative chat-memory providers. It is a destructive
     * delete-then-append operation that wipes every previously stored message for the
     * conversation inside a single transaction — including messages you did not mean to
     * touch. Passing an empty list clears the conversation completely.
     *
     * <p>Why it is dangerous in chat-memory semantics:
     *
     * <ul>
     *   <li>Chat history is append-only by nature. Replacing it wholesale defeats the
     *       purpose of durable conversational memory and silently drops context the LLM
     *       may still rely on.
     *   <li>Concurrent readers will see a brief empty window mid-transaction even with
     *       read-committed isolation (the delete commits before the append fully lands).
     *   <li>The argument list is authoritative: any message not in it is lost with no
     *       recovery path short of a DB backup.
     * </ul>
     *
     * <p>Correct alternatives:
     *
     * <ul>
     *   <li>Normal turn-by-turn writes → {@link #appendAll(String, List)}.
     *   <li>Explicit conversation reset → {@link #deleteByConversationId(String)} followed
     *       by {@link #appendAll(String, List)} in the caller's own transaction, so the
     *       intent is visible at the call site.
     * </ul>
     *
     * <p>The method is retained only because the integration and unit tests in this
     * module assert the delete-then-append contract to guarantee adapter correctness;
     * production callers must stick to {@code appendAll}.
     *
     * @param conversationId the conversation whose history is to be replaced; must be non-null
     * @param messages the replacement history; empty list clears the conversation
     * @deprecated destructive; use {@link #appendAll(String, List)} for normal writes
     *     and {@link #deleteByConversationId(String)} for explicit resets
     */
    @Deprecated
    void saveAll(String conversationId, List<Message> messages);

    /**
     * Removes all stored messages for {@code conversationId}.
     *
     * <p>Use this to permanently forget a conversation (user-initiated clear, retention
     * cleanup, test tear-down). Idempotent: deleting an unknown conversation id is a
     * no-op and does not raise.
     *
     * @param conversationId the conversation to erase; must be non-null
     */
    void deleteByConversationId(String conversationId);
}
