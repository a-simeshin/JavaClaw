package ai.javaclaw.conversations;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-side projections for conversations and their messages, backed by direct
 * JDBC for paginated access patterns that don't fit {@code CrudRepository} or
 * the Spring AI {@code ChatMemoryRepository} (which only exposes full-scan
 * reads per conversation).
 */
@Service
public class ConversationQueryService {

    private final JdbcTemplate jdbc;

    public ConversationQueryService(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Paginated conversation list, newest-first by {@code updated_at}. */
    public Page<ConversationSummary> listConversations(final int page, final int size) {
        final int safeSize = size <= 0 ? 20 : size;
        final int safePage = Math.max(page, 0);
        final int offset = safePage * safeSize;

        // LEFT JOIN with a grouped count so conversations without any message
        // yet still appear (new chats just created from the UI).
        final List<ConversationSummary> rows = jdbc.query(
                """
                SELECT c.id, c.title, c.created_at, c.updated_at,
                       COALESCE(m.msg_count, 0) AS msg_count,
                       (SELECT content FROM SPRING_AI_CHAT_MEMORY
                        WHERE conversation_id = c.id AND type = 'USER'
                        ORDER BY "timestamp" ASC LIMIT 1) AS first_user
                FROM conversations c
                LEFT JOIN (
                    SELECT conversation_id, COUNT(*) AS msg_count
                    FROM SPRING_AI_CHAT_MEMORY
                    GROUP BY conversation_id
                ) m ON m.conversation_id = c.id
                ORDER BY c.updated_at DESC, c.id ASC
                OFFSET ? LIMIT ?
                """,
                (rs, i) -> new ConversationSummary(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getInt("msg_count"),
                        rs.getString("first_user")),
                offset,
                safeSize);

        final Long total = jdbc.queryForObject("SELECT COUNT(*) FROM conversations", Long.class);
        return new Page<>(rows, safePage, safeSize, total == null ? 0L : total);
    }

    /** Paginated messages for one conversation, oldest-first. */
    public Page<MessageRow> listMessages(final String conversationId, final int page, final int size) {
        final int safeSize = size <= 0 ? 50 : size;
        final int safePage = Math.max(page, 0);
        final int offset = safePage * safeSize;

        final List<MessageRow> rows = jdbc.query(
                """
                SELECT content, type, "timestamp"
                FROM SPRING_AI_CHAT_MEMORY
                WHERE conversation_id = ?
                ORDER BY "timestamp" ASC, ctid ASC
                OFFSET ? LIMIT ?
                """,
                (rs, i) -> new MessageRow(
                        rs.getString("content"),
                        rs.getString("type"),
                        rs.getTimestamp("timestamp").toInstant()),
                conversationId,
                offset,
                safeSize);

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
}
