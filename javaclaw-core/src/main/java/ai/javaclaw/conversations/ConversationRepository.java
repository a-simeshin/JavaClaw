package ai.javaclaw.conversations;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC repository for {@link Conversation} entities.
 *
 * <p>The dialect-sensitive {@code UPDATE ... updated_at = now()} statement that
 * used to live here has been extracted to
 * {@link ai.javaclaw.persistence.api.ConversationQueryRepository#touchTitleIfMissing}.
 */
public interface ConversationRepository extends ListCrudRepository<Conversation, String> {

    /** Returns {@code true} if the conversation exists and belongs to the given user. */
    @Query("SELECT COUNT(*) > 0 FROM conversations WHERE id = :id AND user_id = :userId")
    boolean existsByIdAndUserId(@Param("id") String id, @Param("userId") String userId);
}
