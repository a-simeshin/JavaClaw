package ai.javaclaw.channels;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoutingContextTest {

    @Test
    void storesChannelNameAndData() {
        final RoutingContext ctx = new RoutingContext("TelegramChannel", Map.of("chatId", "42"));

        assertThat(ctx.channelName()).isEqualTo("TelegramChannel");
        assertThat(ctx.get("chatId")).isEqualTo("42");
    }

    @Test
    void getNullForMissingKey() {
        final RoutingContext ctx = new RoutingContext("TelegramChannel", Map.of("chatId", "42"));

        assertThat(ctx.get("threadId")).isNull();
    }

    @Test
    void getLongParsesValue() {
        final RoutingContext ctx = new RoutingContext("TelegramChannel", Map.of("chatId", "99"));

        assertThat(ctx.getLong("chatId")).isEqualTo(99L);
    }

    @Test
    void getLongReturnsZeroForMissingKey() {
        final RoutingContext ctx = new RoutingContext("TelegramChannel", Map.of());

        assertThat(ctx.getLong("chatId")).isEqualTo(0L);
    }

    @Test
    void nullDataBecomesEmptyMap() {
        final RoutingContext ctx = new RoutingContext("TelegramChannel", null);

        assertThat(ctx.data()).isEmpty();
        assertThat(ctx.get("anything")).isNull();
    }

    @Test
    void dataIsImmutable() {
        final Map<String, String> mutableMap = new HashMap<>();
        mutableMap.put("chatId", "1");
        final RoutingContext ctx = new RoutingContext("TelegramChannel", mutableMap);

        mutableMap.put("extra", "value");

        assertThat(ctx.data()).doesNotContainKey("extra");
    }
}
