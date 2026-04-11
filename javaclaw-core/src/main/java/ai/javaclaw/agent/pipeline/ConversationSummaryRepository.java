package ai.javaclaw.agent.pipeline;

import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * Repository for conversation summaries used in context window management.
 */
public interface ConversationSummaryRepository extends ListCrudRepository<ConversationSummary, String> {

    @Query("SELECT * FROM conversation_summaries WHERE conversation_id = :convId ORDER BY updated_at DESC LIMIT 1")
    Optional<ConversationSummary> findLatestByConversationId(@Param("convId") String conversationId);

    /** Alias used for cumulative messagesCovered calculation (fix #17). */
    default Optional<ConversationSummary> findByConversationId(String conversationId) {
        return findLatestByConversationId(conversationId);
    }

    @Modifying
    @Query("DELETE FROM conversation_summaries WHERE conversation_id = :convId")
    void deleteByConversationId(@Param("convId") String conversationId);
}
