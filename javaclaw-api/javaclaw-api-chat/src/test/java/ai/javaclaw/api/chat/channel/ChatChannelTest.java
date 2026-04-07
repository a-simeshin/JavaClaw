package ai.javaclaw.api.chat.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.javaclaw.ai.memory.AppendableChatMemoryRepository;
import ai.javaclaw.channels.ChannelRegistry;
import ai.javaclaw.channels.RoutingContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatChannelTest {

    private final AppendableChatMemoryRepository chatMemoryRepository = mock(AppendableChatMemoryRepository.class);

    @Test
    void registersItselfWithChannelRegistryOnConstruction() {
        ChannelRegistry registry = new ChannelRegistry();

        ChatChannel channel = new ChatChannel(registry, chatMemoryRepository);

        assertThat(channel.getName()).isEqualTo("Web Chat Channel");
    }

    @Test
    void sendMessageWritesToChatMemoryForKnownConversation() {
        ChatChannel channel = new ChatChannel(new ChannelRegistry(), chatMemoryRepository);
        RoutingContext ctx = new RoutingContext("Web Chat Channel", Map.of("conversationId", "conv-123"));

        channel.sendMessage(ctx, "Task done!");

        verify(chatMemoryRepository).appendAll(eq("conv-123"), anyList());
    }

    @Test
    void sendMessageDoesNothingWhenConversationIdMissing() {
        ChatChannel channel = new ChatChannel(new ChannelRegistry(), chatMemoryRepository);
        RoutingContext ctx = new RoutingContext("Web Chat Channel", Map.of());

        channel.sendMessage(ctx, "Task done!");

        verifyNoInteractions(chatMemoryRepository);
    }
}
