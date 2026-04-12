package ai.javaclaw.persistence.sqlite;

import ai.javaclaw.persistence.api.ConversationQueryRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * SQLite implementation of {@link ConversationQueryRepository}.
 *
 * <p>Differences from the Postgres impl:
 * <ul>
 *   <li>{@code rowid} replaces {@code ctid} as the physical-row tiebreaker.</li>
 *   <li>{@code timestamp} is unquoted — SQLite does not reserve the identifier.</li>
 *   <li>{@code now()} is replaced with {@code datetime('now')}.</li>
 *   <li>Pagination uses {@code LIMIT ... OFFSET ...} — SQLite's parser rejects
 *       the Postgres {@code OFFSET ... LIMIT ...} order.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "javaclaw.persistence", name = "dialect", havingValue = "sqlite")
public class ConversationQueryRepositorySqliteImpl implements ConversationQueryRepository {

    private static final String FIND_MESSAGES_ORDERED_SQL =
            """
            SELECT content, type, created_at
              FROM SPRING_AI_CHAT_MEMORY
             WHERE conversation_id = :conversationId
             ORDER BY created_at ASC, rowid ASC
             LIMIT :limit OFFSET :offset
            """;

    private static final String TOUCH_TITLE_SQL =
            """
            UPDATE conversations
               SET updated_at = datetime('now'),
                   title      = COALESCE(title, :title)
             WHERE id = :id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ConversationQueryRepositorySqliteImpl(final NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ChatMessageRow> findMessagesOrdered(final String conversationId, final int offset, final int limit) {
        final Map<String, Object> params = new HashMap<>();
        params.put("conversationId", conversationId);
        params.put("offset", offset);
        params.put("limit", limit);
        return jdbc.query(FIND_MESSAGES_ORDERED_SQL, params, (rs, i) -> {
            final Object ts = rs.getObject("created_at");
            return new ChatMessageRow(rs.getString("content"), rs.getString("type"), toInstant(ts));
        });
    }

    /**
     * SQLite's default {@code datetime('now')} format ({@code "YYYY-MM-DD HH:MM:SS"}
     * — space separator, no zone) is parsed alongside ISO-8601 and numeric-epoch forms.
     */
    private static final DateTimeFormatter SQLITE_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static Instant toInstant(final Object ts) {
        if (ts == null) {
            return null;
        }
        if (ts instanceof Timestamp t) {
            return t.toInstant();
        }
        if (ts instanceof Number n) {
            return Instant.ofEpochMilli(n.longValue());
        }
        final String str = ts.toString();
        try {
            return Instant.parse(str);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(str, SQLITE_DATETIME).toInstant(ZoneOffset.UTC);
        }
    }

    @Override
    public int touchTitleIfMissing(final String conversationId, final String title) {
        final Map<String, Object> params = new HashMap<>();
        params.put("id", conversationId);
        params.put("title", title);
        return jdbc.update(TOUCH_TITLE_SQL, params);
    }
}
