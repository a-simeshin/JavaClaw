package ai.javaclaw.api.chat;

import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * GUI channel for the web chat interface.
 * Chat traffic flows through {@code ChatRestController} + {@code SseStreamingService}
 * (React SPA). Background-task notifications delivered via {@link #sendMessage(String)}
 * are logged only — no active push transport.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ChatChannel implements Channel {

    public ChatChannel(final ChannelRegistry channelRegistry) {
        channelRegistry.registerChannel(this);
        log.info("Started Web Chat channel");
    }

    @Override
    public String getName() {
        return "Web Chat Channel";
    }

    @Override
    public void sendMessage(String message) {
        log.debug("Web Chat background message dropped (no active transport): {}", message);
    }
}
