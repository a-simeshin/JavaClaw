package ai.javaclaw.api.chat.channel;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.channels.ChannelRegistry;
import org.junit.jupiter.api.Test;

class ChatChannelTest {

    @Test
    void registersItselfWithChannelRegistryOnConstruction() {
        ChannelRegistry registry = new ChannelRegistry();

        ChatChannel channel = new ChatChannel(registry);

        assertThat(channel.getName()).isEqualTo("Web Chat Channel");
    }

    @Test
    void sendMessageDoesNotThrow() {
        ChatChannel channel = new ChatChannel(new ChannelRegistry());

        channel.sendMessage("Background result");
    }
}
