package ai.javaclaw.channels;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChannelRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChannelRegistry.class);

    private final Map<String, Channel> channels;
    private volatile String defaultChannelName;

    public ChannelRegistry() {
        this.channels = new ConcurrentHashMap<>();
    }

    public void registerChannel(Channel channel) {
        channels.put(channel.getName(), channel);
        if (channels.size() == 1) {
            this.defaultChannelName = channel.getName();
        }
    }

    public void unregisterChannel(Channel channel) {
        channels.remove(channel.getName());
    }

    /**
     * Looks up a channel by name. Falls back to the default channel if the given name
     * is {@code null} or not found in the registry.
     */
    public Channel getChannel(String name) {
        if (name != null) {
            Channel channel = channels.get(name);
            if (channel != null) {
                return channel;
            }
        }
        return channels.get(defaultChannelName);
    }

    /**
     * @deprecated No longer used for channel routing. Tasks now carry {@code sourceChannelName}
     * and routing is done via {@link #getChannel(String)}. Kept for backward compatibility
     * with existing channel implementations.
     */
    @Deprecated
    public void publishMessageReceivedEvent(ChannelMessageReceivedEvent event) {
        log.debug("publishMessageReceivedEvent called (deprecated): {}", event.getChannel());
    }
}
