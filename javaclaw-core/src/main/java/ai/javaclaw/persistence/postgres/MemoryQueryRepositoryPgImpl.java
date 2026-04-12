package ai.javaclaw.persistence.postgres;

import ai.javaclaw.memory.Memory;
import ai.javaclaw.persistence.api.MemoryQueryRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Postgres implementation of {@link MemoryQueryRepository}. Uses the Postgres
 * {@code ILIKE} operator for ASCII case-insensitive substring matching.
 */
@Component
@ConditionalOnProperty(
        prefix = "javaclaw.persistence",
        name = "dialect",
        havingValue = "postgresql",
        matchIfMissing = true)
public class MemoryQueryRepositoryPgImpl implements MemoryQueryRepository {

    private static final String SEARCH_OWNER_SQL =
            """
            SELECT id, owner_id, key, content, category, created_at, updated_at
              FROM memories
             WHERE owner_id = :ownerId
               AND (key     ILIKE '%' || :query || '%'
                    OR content ILIKE '%' || :query || '%')
             ORDER BY updated_at DESC
            """;

    private static final String SEARCH_GLOBAL_SQL =
            """
            SELECT id, owner_id, key, content, category, created_at, updated_at
              FROM memories
             WHERE owner_id IS NULL
               AND (key     ILIKE '%' || :query || '%'
                    OR content ILIKE '%' || :query || '%')
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

    public MemoryQueryRepositoryPgImpl(final NamedParameterJdbcTemplate jdbc) {
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

    private static Instant toInstant(final ResultSet rs, final String column) throws SQLException {
        final Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }
}
