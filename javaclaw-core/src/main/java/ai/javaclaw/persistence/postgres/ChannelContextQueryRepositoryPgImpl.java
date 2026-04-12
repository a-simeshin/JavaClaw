package ai.javaclaw.persistence.postgres;

import ai.javaclaw.persistence.api.ChannelContextQueryRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Postgres implementation of {@link ChannelContextQueryRepository}. Uses
 * {@code ON CONFLICT (conversation_id) DO UPDATE} with {@code EXCLUDED.*}
 * references — standard Postgres upsert.
 */
@Component
@ConditionalOnProperty(
        prefix = "javaclaw.persistence",
        name = "dialect",
        havingValue = "postgresql",
        matchIfMissing = true)
public class ChannelContextQueryRepositoryPgImpl implements ChannelContextQueryRepository {

    private static final String UPSERT_SQL =
            """
            INSERT INTO conversation_channel_context (conversation_id, channel_name, routing_data, updated_at)
            VALUES (:conversationId, :channelName, :routingData, :updatedAt)
            ON CONFLICT (conversation_id) DO UPDATE SET
                channel_name = EXCLUDED.channel_name,
                routing_data = EXCLUDED.routing_data,
                updated_at   = EXCLUDED.updated_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ChannelContextQueryRepositoryPgImpl(final NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(
            final String conversationId, final String channelName, final String routingData, final Instant updatedAt) {
        final Map<String, Object> params = new HashMap<>();
        params.put("conversationId", conversationId);
        params.put("channelName", channelName);
        params.put("routingData", routingData);
        params.put("updatedAt", Timestamp.from(updatedAt));
        jdbc.update(UPSERT_SQL, params);
    }
}
