package ai.javaclaw.persistence.sqlite;

import ai.javaclaw.memory.Memory;
import ai.javaclaw.persistence.api.MemoryQueryRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * SQLite implementation of {@link MemoryQueryRepository}. Replaces the
 * Postgres {@code ILIKE} operator with {@code LIKE ... COLLATE NOCASE};
 * SQLite's {@code NOCASE} collation is ASCII-only, matching the practical
 * behaviour of Postgres {@code ILIKE}.
 */
@Component
@ConditionalOnProperty(prefix = "javaclaw.persistence", name = "dialect", havingValue = "sqlite")
public class MemoryQueryRepositorySqliteImpl implements MemoryQueryRepository {

    private static final String SEARCH_OWNER_SQL =
            """
            SELECT id, owner_id, key, content, category, created_at, updated_at
              FROM memories
             WHERE owner_id = :ownerId
               AND (key     LIKE '%' || :query || '%' COLLATE NOCASE
                    OR content LIKE '%' || :query || '%' COLLATE NOCASE)
             ORDER BY updated_at DESC
            """;

    private static final String SEARCH_GLOBAL_SQL =
            """
            SELECT id, owner_id, key, content, category, created_at, updated_at
              FROM memories
             WHERE owner_id IS NULL
               AND (key     LIKE '%' || :query || '%' COLLATE NOCASE
                    OR content LIKE '%' || :query || '%' COLLATE NOCASE)
             ORDER BY updated_at DESC
            """;

    private static final RowMapper<Memory> ROW_MAPPER = (rs, i) -> new Memory(
            rs.getString("id"),
            rs.getString("owner_id"),
            rs.getString("key"),
            rs.getString("content"),
            rs.getString("category"),
            toInstant(rs, "created_at"),
            toInstant(rs, "updated_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public MemoryQueryRepositorySqliteImpl(final NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Memory> searchForOwner(final String ownerId, final String query) {
        final Map<String, Object> params = new HashMap<>();
        params.put("ownerId", ownerId);
        params.put("query", query);
        return jdbc.query(SEARCH_OWNER_SQL, params, ROW_MAPPER);
    }

    @Override
    public List<Memory> searchGlobal(final String query) {
        return jdbc.query(SEARCH_GLOBAL_SQL, Map.of("query", query), ROW_MAPPER);
    }

    /**
     * SQLite's default {@code datetime('now')} format ({@code "YYYY-MM-DD HH:MM:SS"}
     * — space separator, no zone) is parsed alongside ISO-8601 and numeric-epoch forms.
     * All values are treated as UTC because the underlying {@code datetime('now')}
     * returns UTC per SQLite documentation.
     */
    private static final DateTimeFormatter SQLITE_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static Instant toInstant(final ResultSet rs, final String column) throws SQLException {
        final Object ts = rs.getObject(column);
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
}
