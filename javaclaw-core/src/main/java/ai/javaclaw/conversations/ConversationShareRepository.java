package ai.javaclaw.conversations;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Spring Data JDBC repository for {@link ConversationShare} entities. */
public interface ConversationShareRepository extends ListCrudRepository<ConversationShare, Long> {

    /** All shares for a given conversation. */
    List<ConversationShare> findByConversationId(String conversationId);

    /** All conversations shared with a given user. */
    List<ConversationShare> findBySharedWith(String sharedWith);

    /** Check if a specific share exists. */
    boolean existsByConversationIdAndSharedWith(String conversationId, String sharedWith);

    /** Find a specific share. */
    Optional<ConversationShare> findByConversationIdAndSharedWith(String conversationId, String sharedWith);

    /** Remove a specific share. */
    @Modifying
    @Query("DELETE FROM conversation_shares WHERE conversation_id = :conversationId AND shared_with = :sharedWith")
    int deleteByConversationIdAndSharedWith(
            @Param("conversationId") String conversationId, @Param("sharedWith") String sharedWith);

    /** Remove all shares for a conversation. */
    @Modifying
    @Query("DELETE FROM conversation_shares WHERE conversation_id = :conversationId")
    int deleteAllByConversationId(@Param("conversationId") String conversationId);
}
