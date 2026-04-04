package ai.javaclaw.channels;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelRegistryTest {

    ChannelRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ChannelRegistry();
    }

    @Test
    void registerChannelAddsAndCanRetrieveByName() {
        Channel channel = stubChannel("telegram");

        registry.registerChannel(channel);

        assertThat(registry.getChannel("telegram")).isSameAs(channel);
    }

    @Test
    void getChannelReturnsCorrectChannelByName() {
        Channel telegram = stubChannel("telegram");
        Channel discord = stubChannel("discord");
        registry.registerChannel(telegram);
        registry.registerChannel(discord);

        assertThat(registry.getChannel("telegram")).isSameAs(telegram);
        assertThat(registry.getChannel("discord")).isSameAs(discord);
    }

    @Test
    void getChannelFallsBackToDefaultForUnknownName() {
        Channel defaultChannel = stubChannel("chat");
        registry.registerChannel(defaultChannel);

        Channel result = registry.getChannel("nonexistent");

        assertThat(result).isSameAs(defaultChannel);
    }

    @Test
    void getChannelFallsBackToDefaultForNull() {
        Channel defaultChannel = stubChannel("chat");
        registry.registerChannel(defaultChannel);

        Channel result = registry.getChannel(null);

        assertThat(result).isSameAs(defaultChannel);
    }

    @Test
    void firstRegisteredChannelBecomesDefault() {
        Channel first = stubChannel("first");
        Channel second = stubChannel("second");
        registry.registerChannel(first);
        registry.registerChannel(second);

        // null name should fall back to default (first registered)
        assertThat(registry.getChannel(null)).isSameAs(first);
    }

    @Test
    void unregisterChannelRemovesChannel() {
        Channel channel = stubChannel("telegram");
        registry.registerChannel(channel);

        registry.unregisterChannel(channel);

        assertThat(registry.getChannel("telegram")).isNull();
    }

    private static Channel stubChannel(String name) {
        return new Channel() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public void sendMessage(String message) {
                // no-op
            }
        };
    }
}
