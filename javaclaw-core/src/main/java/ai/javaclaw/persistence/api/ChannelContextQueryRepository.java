package ai.javaclaw.persistence.api;

import java.time.Instant;

/**
 * Dialect-sensitive queries for the {@code conversation_channel_context} table.
 *
 * <p>Extracted from {@link ai.javaclaw.channels.ChannelContextService} to keep the
 * Postgres-flavoured {@code ON CONFLICT ... DO UPDATE} and the SQLite-flavoured upsert
 * behind a single interface. Implementations are dialect-conditional beans selected via
 * {@code javaclaw.persistence.dialect}.
 */
public interface ChannelContextQueryRepository {

    /**
     * Upserts the routing context row for the given conversation.
     *
     * @param conversationId conversation identifier (primary key)
     * @param channelName    channel bean name (e.g. {@code TelegramChannel})
     * @param routingData    serialized channel-specific routing data (JSON string)
     * @param updatedAt      update timestamp (wall-clock instant)
     */
    void upsert(String conversationId, String channelName, String routingData, Instant updatedAt);
}
