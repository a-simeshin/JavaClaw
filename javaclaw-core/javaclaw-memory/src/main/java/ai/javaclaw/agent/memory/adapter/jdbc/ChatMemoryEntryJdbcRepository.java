package ai.javaclaw.agent.memory.adapter.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.RowMapper;

/**
 * Spring Data JDBC repository for {@link ChatMemoryEntry}.
 *
 * <p>Provides ordered retrieval, targeted deletion and distinct-id enumeration
 * by {@code conversation_id}. All chat-memory SQL is consolidated here —
 * {@link JdbcChatMemory} is a pure adapter over this repository.
 */
public interface ChatMemoryEntryJdbcRepository extends ListCrudRepository<ChatMemoryEntry, Long> {

    /**
     * Returns all entries for {@code conversationId} ordered by {@code id} ascending.
     *
     * <p>{@code id} (AUTOINCREMENT / GENERATED ALWAYS AS IDENTITY) is strictly monotonic and
     * reflects true insertion order across batches. Using {@code created_at} alone is
     * insufficient because multiple {@link JdbcChatMemory#appendAll(String, java.util.List)}
     * calls within the same wall-clock second share the same second-level timestamp.
     */
    @Query("SELECT * FROM spring_ai_chat_memory WHERE conversation_id = :conversationId ORDER BY id")
    List<ChatMemoryEntry> findByConversationId(@Param("conversationId") String conversationId);

    /**
     * Deletes all entries for {@code conversationId}.
     *
     * <p>Used by {@link JdbcChatMemory#deleteByConversationId(String)} and by the transactional
     * {@link JdbcChatMemory#saveAll(String, java.util.List)} (delete-then-append pattern).
     */
    @Modifying
    @Query("DELETE FROM spring_ai_chat_memory WHERE conversation_id = :conversationId")
    void deleteByConversationId(@Param("conversationId") String conversationId);

    /**
     * Returns the distinct set of {@code conversation_id}s that have at least one memory entry.
     *
     * <p>Uses {@link ConversationIdRowMapper} via {@code rowMapperClass} because Spring Data JDBC's
     * default {@code RowMapper} targets the aggregate root ({@link ChatMemoryEntry}) and cannot
     * project a single {@code TEXT} column into {@code String}.
     */
    @Query(
            value = "SELECT DISTINCT conversation_id FROM spring_ai_chat_memory",
            rowMapperClass = ConversationIdRowMapper.class)
    List<String> findDistinctConversationIds();

    /**
     * {@link RowMapper} extracting a single {@code conversation_id} column as {@link String}.
     *
     * <p>Package-private and instantiated by Spring Data JDBC via its no-arg constructor
     * (see {@link Query#rowMapperClass()}). Kept in this file because it exists solely to
     * serve {@link ChatMemoryEntryJdbcRepository#findDistinctConversationIds()}.
     */
    class ConversationIdRowMapper implements RowMapper<String> {

        @Override
        public String mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            return rs.getString(1);
        }
    }
}
