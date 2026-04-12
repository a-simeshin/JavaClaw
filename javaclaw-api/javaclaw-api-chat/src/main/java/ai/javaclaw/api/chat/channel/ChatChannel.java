package ai.javaclaw.api.chat.channel;

import ai.javaclaw.agent.memory.ChatMemory;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelRegistry;
import ai.javaclaw.channels.RoutingContext;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * GUI channel for the web chat interface.
 * Chat traffic flows through {@code ChatRestController} + {@code SseStreamingService}
 * (React SPA). Background-task notifications are written to chat memory as AssistantMessages
 * so the frontend picks them up on the next polling cycle.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ChatChannel implements Channel {

    private final ChatMemory chatMemoryRepository;

    public ChatChannel(final ChannelRegistry channelRegistry, final ChatMemory chatMemoryRepository) {
        this.chatMemoryRepository = chatMemoryRepository;
        channelRegistry.registerChannel(this);
        log.info("Started Web Chat channel");
    }

    @Override
    public String getName() {
        return "Web Chat Channel";
    }

    /**
     * Writes the task notification to chat memory as an AssistantMessage.
     * The frontend picks it up on the next polling or re-render cycle.
     *
     * @param routingContext must contain {@code conversationId} in its data map
     * @param message        notification text to deliver
     */
    @Override
    public void sendMessage(final RoutingContext routingContext, final String message) {
        final String conversationId = routingContext.get("conversationId");
        if (conversationId == null) {
            log.warn("No conversationId in RoutingContext for Web Chat Channel, dropping message: {}", message);
            return;
        }
        final AssistantMessage notification = AssistantMessage.builder()
                .content("[Task notification] " + message)
                .build();
        chatMemoryRepository.appendAll(conversationId, List.of(notification));
        log.debug("Task notification written to chat memory for conversation {}", conversationId);
    }
}
