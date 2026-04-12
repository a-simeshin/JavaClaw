package ai.javaclaw.persistence.sqlite;

import ai.javaclaw.persistence.api.ChannelContextQueryRepository;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * SQLite implementation of {@link ChannelContextQueryRepository}. SQLite 3.24+
 * accepts the same {@code ON CONFLICT ... DO UPDATE} / {@code excluded.*}
 * syntax as Postgres; the impl is duplicated to keep the dialect-dispatch
 * pattern uniform and to leave room for future divergence.
 *
 * <p>Timestamps are stored as ISO-8601 strings (UTC) to match the SQLite
 * {@code Instant}/{@code String} converter used project-wide.
 */
@Component
@ConditionalOnProperty(prefix = "javaclaw.persistence", name = "dialect", havingValue = "sqlite")
public class ChannelContextQueryRepositorySqliteImpl implements ChannelContextQueryRepository {

    private static final String UPSERT_SQL =
            """
            INSERT INTO conversation_channel_context (conversation_id, channel_name, routing_data, updated_at)
            VALUES (:conversationId, :channelName, :routingData, :updatedAt)
            ON CONFLICT (conversation_id) DO UPDATE SET
                channel_name = excluded.channel_name,
                routing_data = excluded.routing_data,
                updated_at   = excluded.updated_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ChannelContextQueryRepositorySqliteImpl(final NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(
            final String conversationId, final String channelName, final String routingData, final Instant updatedAt) {
        final Map<String, Object> params = new HashMap<>();
        params.put("conversationId", conversationId);
        params.put("channelName", channelName);
        params.put("routingData", routingData);
        params.put("updatedAt", DateTimeFormatter.ISO_INSTANT.format(updatedAt));
        jdbc.update(UPSERT_SQL, params);
    }
}
