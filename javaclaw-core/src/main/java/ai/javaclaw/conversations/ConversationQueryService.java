package ai.javaclaw.conversations;

import ai.javaclaw.persistence.api.ConversationQueryRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-side projections for conversations and their messages, backed by direct
 * JDBC for paginated access patterns that don't fit {@code CrudRepository} or
 * the {@code ChatMemory} port (which only exposes full-scan reads per
 * conversation).
 *
 * <p>Dialect-sensitive slicing of {@code SPRING_AI_CHAT_MEMORY} (ctid vs rowid
 * tiebreaker) is delegated to {@link ConversationQueryRepository}; the rest of
 * the SQL on this class stays ANSI-portable (GROUP BY, COALESCE, sub-SELECT,
 * COUNT(*)).
 */
@Service
public class ConversationQueryService {

    private final JdbcTemplate jdbc;
    private final ConversationQueryRepository queryRepository;

    public ConversationQueryService(final JdbcTemplate jdbc, final ConversationQueryRepository queryRepository) {
        this.jdbc = jdbc;
        this.queryRepository = queryRepository;
    }

    /** Paginated conversation list, newest-first by {@code updated_at}. Returns all conversations. */
    public Page<ConversationSummary> listConversations(final int page, final int size) {
        return listConversationsForUser(null, page, size);
    }

    /**
     * Paginated conversation list filtered by user, newest-first by {@code updated_at}.
     *
     * @param userId the owning user's database ID; if {@code null}, returns all conversations
     */
    public Page<ConversationSummary> listConversationsForUser(final String userId, final int page, final int size) {
        final int safeSize = size <= 0 ? 20 : size;
        final int safePage = Math.max(page, 0);
        final int offset = safePage * safeSize;

        final String whereClause = userId != null ? "WHERE c.user_id = ?" : "";
        final String countWhereClause = userId != null ? "WHERE user_id = ?" : "";

        // LEFT JOIN with a grouped count so conversations without any message
        // yet still appear (new chats just created from the UI).
        // Uses portable `LIMIT n OFFSET m` form (accepted by both Postgres and SQLite).
        final String sql =
                """
                SELECT c.id, c.title, c.created_at, c.updated_at,
                       COALESCE(m.msg_count, 0) AS msg_count,
                       (SELECT content FROM SPRING_AI_CHAT_MEMORY
                        WHERE conversation_id = c.id AND type = 'USER'
                        ORDER BY created_at ASC LIMIT 1) AS first_user
                FROM conversations c
                LEFT JOIN (
                    SELECT conversation_id, COUNT(*) AS msg_count
                    FROM SPRING_AI_CHAT_MEMORY
                    GROUP BY conversation_id
                ) m ON m.conversation_id = c.id
                %s
                ORDER BY c.updated_at DESC, c.id ASC
                LIMIT ? OFFSET ?
                """
                        .formatted(whereClause);

        final List<ConversationSummary> rows;
        if (userId != null) {
            rows = jdbc.query(
                    sql,
                    (rs, i) -> new ConversationSummary(
                            rs.getString("id"),
                            rs.getString("title"),
                            readInstant(rs, "created_at"),
                            readInstant(rs, "updated_at"),
                            rs.getInt("msg_count"),
                            rs.getString("first_user")),
                    userId,
                    safeSize,
                    offset);
        } else {
            rows = jdbc.query(
                    sql,
                    (rs, i) -> new ConversationSummary(
                            rs.getString("id"),
                            rs.getString("title"),
                            readInstant(rs, "created_at"),
                            readInstant(rs, "updated_at"),
                            rs.getInt("msg_count"),
                            rs.getString("first_user")),
                    safeSize,
                    offset);
        }

        final Long total;
        if (userId != null) {
            total = jdbc.queryForObject("SELECT COUNT(*) FROM conversations " + countWhereClause, Long.class, userId);
        } else {
            total = jdbc.queryForObject("SELECT COUNT(*) FROM conversations", Long.class);
        }
        return new Page<>(rows, safePage, safeSize, total == null ? 0L : total);
    }

    /**
     * Paginated conversation list including both owned and shared conversations.
     *
     * @param userId the user's database ID
     */
    public Page<ConversationSummary> listConversationsWithShared(final String userId, final int page, final int size) {
        final int safeSize = size <= 0 ? 20 : size;
        final int safePage = Math.max(page, 0);
        final int offset = safePage * safeSize;

        // Portable `LIMIT n OFFSET m` form (accepted by both Postgres and SQLite).
        final String sql =
                """
                SELECT c.id, c.title, c.created_at, c.updated_at,
                       COALESCE(m.msg_count, 0) AS msg_count,
                       (SELECT content FROM SPRING_AI_CHAT_MEMORY
                        WHERE conversation_id = c.id AND type = 'USER'
                        ORDER BY created_at ASC LIMIT 1) AS first_user
                FROM conversations c
                LEFT JOIN (
                    SELECT conversation_id, COUNT(*) AS msg_count
                    FROM SPRING_AI_CHAT_MEMORY
                    GROUP BY conversation_id
                ) m ON m.conversation_id = c.id
                WHERE c.user_id = ?
                   OR c.id IN (SELECT conversation_id FROM conversation_shares WHERE shared_with = ?)
                ORDER BY c.updated_at DESC, c.id ASC
                LIMIT ? OFFSET ?
                """;

        final List<ConversationSummary> rows = jdbc.query(
                sql,
                (rs, i) -> new ConversationSummary(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getInt("msg_count"),
                        rs.getString("first_user")),
                userId,
                userId,
                safeSize,
                offset);

        final Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM conversations WHERE user_id = ? OR id IN (SELECT conversation_id FROM conversation_shares WHERE shared_with = ?)",
                Long.class,
                userId,
                userId);
        return new Page<>(rows, safePage, safeSize, total == null ? 0L : total);
    }

    /** Paginated messages for one conversation, oldest-first. */
    public Page<MessageRow> listMessages(final String conversationId, final int page, final int size) {
        final int safeSize = size <= 0 ? 50 : size;
        final int safePage = Math.max(page, 0);
        final int offset = safePage * safeSize;

        final List<MessageRow> rows = queryRepository.findMessagesOrdered(conversationId, offset, safeSize).stream()
                .map(r -> new MessageRow(r.content(), r.type(), r.createdAt()))
                .toList();

        final Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?", Long.class, conversationId);
        return new Page<>(rows, safePage, safeSize, total == null ? 0L : total);
    }

    /** Summary projection of a conversation for list endpoints. */
    public record ConversationSummary(
            String id, String title, Instant createdAt, Instant updatedAt, int messageCount, String firstUserMessage) {}

    /** One row from {@code SPRING_AI_CHAT_MEMORY} with its real DB timestamp. */
    public record MessageRow(String content, String type, Instant createdAt) {}

    /** Minimal paginated envelope. */
    public record Page<T>(List<T> content, int page, int size, long total) {}

    /**
     * Reads an Instant from a ResultSet column that may be stored as either a native
     * TIMESTAMP (PostgreSQL) or an ISO-8601 TEXT string (SQLite).
     */
    private static Instant readInstant(ResultSet rs, String column) throws SQLException {
        String raw = rs.getString(column);
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        // ISO-8601 from SQLite InstantToStringConverter (contains 'T')
        if (raw.contains("T")) {
            try {
                return Instant.parse(raw);
            } catch (DateTimeParseException e) {
                return Instant.now();
            }
        }
        // Native JDBC Timestamp (PostgreSQL) — format "2026-04-12 10:04:05.123+00"
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : Instant.now();
    }
}
