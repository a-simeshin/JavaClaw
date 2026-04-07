package ai.javaclaw.channels;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable routing context for delivering async task notifications back to the originating channel.
 * Each channel implementation populates {@code data} with channel-specific identifiers
 * (e.g. {@code chatId}/{@code threadId} for Telegram, {@code channelId} for Discord,
 * {@code conversationId} for Web Chat).
 */
public record RoutingContext(String channelName, Map<String, String> data) {

    public RoutingContext {
        data = data == null ? Collections.emptyMap() : Map.copyOf(data);
    }

    /** Returns the routing data value for the given key, or {@code null} if absent. */
    public String get(final String key) {
        return data.get(key);
    }

    /** Returns the routing data value for the given key parsed as {@code long}, or {@code 0} if absent. */
    public long getLong(final String key) {
        final String value = data.get(key);
        return value == null ? 0L : Long.parseLong(value);
    }
}
