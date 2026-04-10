package ai.javaclaw.conversations;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Spring Data JDBC repository for {@link Conversation} entities. */
public interface ConversationRepository extends ListCrudRepository<Conversation, String> {

    /**
     * Sets {@code updated_at = now()} and, when {@code title} is still NULL,
     * initialises it with the supplied value (truncated preview of the first
     * user message). Subsequent calls never overwrite an existing title.
     */
    @Modifying
    @Query("UPDATE conversations SET updated_at = now(), " + "title = COALESCE(title, :title) WHERE id = :id")
    int touchWithTitle(@Param("id") String id, @Param("title") String title);

    /** Returns {@code true} if the conversation exists and belongs to the given user. */
    @Query("SELECT COUNT(*) > 0 FROM conversations WHERE id = :id AND user_id = :userId")
    boolean existsByIdAndUserId(@Param("id") String id, @Param("userId") String userId);
}
