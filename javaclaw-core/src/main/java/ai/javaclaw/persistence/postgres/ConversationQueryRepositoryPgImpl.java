package ai.javaclaw.persistence.postgres;

import ai.javaclaw.persistence.api.ConversationQueryRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Postgres implementation of {@link ConversationQueryRepository}. Uses
 * {@code ctid} as the physical-row tiebreaker and double-quotes the
 * {@code "created_at"} column (renamed from {@code "timestamp"} in V43).
 */
@Component
@ConditionalOnProperty(
        prefix = "javaclaw.persistence",
        name = "dialect",
        havingValue = "postgresql",
        matchIfMissing = true)
public class ConversationQueryRepositoryPgImpl implements ConversationQueryRepository {

    private static final String FIND_MESSAGES_ORDERED_SQL =
            """
            SELECT content, type, created_at
              FROM SPRING_AI_CHAT_MEMORY
             WHERE conversation_id = :conversationId
             ORDER BY created_at ASC, ctid ASC
             OFFSET :offset LIMIT :limit
            """;

    private static final String TOUCH_TITLE_SQL =
            """
            UPDATE conversations
               SET updated_at = now(),
                   title      = COALESCE(title, :title)
             WHERE id = :id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ConversationQueryRepositoryPgImpl(final NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ChatMessageRow> findMessagesOrdered(final String conversationId, final int offset, final int limit) {
        final Map<String, Object> params = new HashMap<>();
        params.put("conversationId", conversationId);
        params.put("offset", offset);
        params.put("limit", limit);
        return jdbc.query(
                FIND_MESSAGES_ORDERED_SQL,
                params,
                (rs, i) -> new ChatMessageRow(
                        rs.getString("content"),
                        rs.getString("type"),
                        rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public int touchTitleIfMissing(final String conversationId, final String title) {
        final Map<String, Object> params = new HashMap<>();
        params.put("id", conversationId);
        params.put("title", title);
        return jdbc.update(TOUCH_TITLE_SQL, params);
    }
}
