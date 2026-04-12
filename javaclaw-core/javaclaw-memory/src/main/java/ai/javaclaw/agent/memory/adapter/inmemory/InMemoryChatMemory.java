package ai.javaclaw.agent.memory.adapter.inmemory;

import ai.javaclaw.agent.memory.ChatMemory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.Message;

/**
 * Thread-unsafe in-memory {@link ChatMemory} adapter intended for unit tests and local
 * dev profiles that do not need persistence.
 *
 * <p>Backed by a {@link LinkedHashMap} keyed on {@code conversationId}, which gives
 * deterministic iteration order for {@link #findConversationIds()} based on first-insert
 * time. This is convenient for tests but <em>not</em> part of the {@link ChatMemory}
 * ordering contract — callers should not depend on it.
 *
 * <h2>Thread safety</h2>
 * Not thread-safe. All state lives in an unsynchronised {@link LinkedHashMap} and inner
 * {@link ArrayList}s. Use only from a single test thread or wrap externally with a lock.
 *
 * <h2>Bean wiring</h2>
 * Never registered as a Spring bean. Instantiate directly in tests that need a simple
 * non-mock stand-in for {@link ChatMemory} (e.g. when Mockito stubbing would be noisier
 * than a real collection-backed implementation).
 */
public class InMemoryChatMemory implements ChatMemory {

    /**
     * Per-conversation message store.
     *
     * <p>Keys are {@code conversationId}s; values are mutable {@link ArrayList}s of
     * messages in append order. Uses {@link LinkedHashMap} so
     * {@link #findConversationIds()} returns ids in first-insert order — useful for
     * deterministic tests though not guaranteed by the {@link ChatMemory} contract.
     */
    private final Map<String, List<Message>> store = new LinkedHashMap<>();

    /**
     * {@inheritDoc}
     *
     * <p>Returns a defensive copy of the key set so callers mutating the returned list
     * cannot corrupt the store.
     */
    @Override
    public List<String> findConversationIds() {
        return List.copyOf(store.keySet());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns a defensive copy; an empty list is returned if {@code conversationId}
     * is unknown. The underlying {@link ArrayList} is never exposed.
     */
    @Override
    public List<Message> findByConversationId(final String conversationId) {
        return List.copyOf(store.getOrDefault(conversationId, List.of()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Honors the port's null-is-noop / empty-is-noop contract: if {@code messages}
     * is {@code null} or empty the call returns without creating a per-conversation
     * entry in the backing map, so {@link #findConversationIds()} will not start
     * reporting the conversation as a side effect of a no-op write. Otherwise, lazily
     * creates the per-conversation list on first write and appends in order.
     */
    @Override
    public void appendAll(final String conversationId, final List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        store.computeIfAbsent(conversationId, k -> new ArrayList<>()).addAll(messages);
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>⚠ Destructive — present only to honor the {@link ChatMemory} contract.</strong>
     * Implemented as a delete-then-append to mirror {@code JdbcChatMemory.saveAll} so
     * that behavioral assertions written against the JDBC adapter stay valid when
     * swapped onto this in-memory stand-in. Consequently {@code saveAll(id, List.of())}
     * removes the conversation entirely — {@link #findConversationIds()} will no longer
     * report it — which matches the port contract. Test code exercising
     * delete-then-replace scenarios is the only legitimate caller; production code must
     * use {@link #appendAll(String, List)}.
     *
     * @deprecated see {@link ChatMemory#saveAll(String, List)}
     */
    @Override
    @Deprecated
    public void saveAll(final String conversationId, final List<Message> messages) {
        deleteByConversationId(conversationId);
        appendAll(conversationId, messages);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Removes the entry for {@code conversationId} entirely — after this call
     * {@link #findConversationIds()} will no longer include it. Idempotent: deleting an
     * unknown id is a no-op.
     */
    @Override
    public void deleteByConversationId(final String conversationId) {
        store.remove(conversationId);
    }
}
