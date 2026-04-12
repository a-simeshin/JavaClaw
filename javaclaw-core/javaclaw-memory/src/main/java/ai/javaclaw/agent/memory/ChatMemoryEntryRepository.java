package ai.javaclaw.agent.memory;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC repository for {@link ChatMemoryEntry}.
 *
 * <p>Provides ordered retrieval and targeted deletion by {@code conversation_id}.
 * {@link #findConversationIds()} is intentionally omitted: scalar {@code List<String>}
 * projection from {@code @Query} is not reliably supported by Spring Data JDBC's
 * public API, so that method lives in {@link SpringDataChatMemoryRepository} via
 * {@code NamedParameterJdbcTemplate}.
 */
public interface ChatMemoryEntryRepository extends ListCrudRepository<ChatMemoryEntry, Long> {

    /**
     * Returns all entries for {@code conversationId} ordered by {@code id} ascending.
     *
     * <p>{@code id} (AUTOINCREMENT / GENERATED ALWAYS AS IDENTITY) is strictly monotonic and
     * reflects true insertion order across batches. Using {@code created_at} alone is insufficient
     * because multiple {@link SpringDataChatMemoryRepository#appendAll} calls within the same
     * wall-clock second share the same second-level timestamp.
     */
    @Query("SELECT * FROM spring_ai_chat_memory WHERE conversation_id = :conversationId ORDER BY id")
    List<ChatMemoryEntry> findByConversationId(@Param("conversationId") String conversationId);

    /**
     * Deletes all entries for {@code conversationId}.
     *
     * <p>Used by {@link SpringDataChatMemoryRepository#deleteByConversationId(String)} and
     * by the transactional {@link SpringDataChatMemoryRepository#saveAll(String, java.util.List)}
     * (delete-then-append pattern).
     */
    @Modifying
    @Query("DELETE FROM spring_ai_chat_memory WHERE conversation_id = :conversationId")
    void deleteByConversationId(@Param("conversationId") String conversationId);
}
