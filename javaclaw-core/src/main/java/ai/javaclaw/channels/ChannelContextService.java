package ai.javaclaw.channels;

import ai.javaclaw.persistence.api.ChannelContextQueryRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Saves and retrieves per-conversation channel routing context.
 *
 * <p>When a channel receives a message it calls {@link #saveContext} to record which channel
 * (and channel-specific routing data such as chatId/channelId) is associated with that
 * conversation. Later, when an async task finishes, {@link #getContext} is used to find
 * where to deliver the notification.
 *
 * <p>The dialect-sensitive upsert SQL is isolated behind
 * {@link ChannelContextQueryRepository} so the service stays database-agnostic.
 */
@Service
public class ChannelContextService {

    private final ConversationChannelContextRepository repository;
    private final ChannelContextQueryRepository queryRepository;
    private final ObjectMapper objectMapper;

    public ChannelContextService(
            final ConversationChannelContextRepository repository,
            final ChannelContextQueryRepository queryRepository,
            final ObjectMapper objectMapper) {
        this.repository = repository;
        this.queryRepository = queryRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists (upserts) the routing context for the given conversation.
     *
     * @param conversationId unique conversation identifier, must not be blank
     * @param channelName    channel name as returned by {@link Channel#getName()}, must not be blank
     * @param routingData    channel-specific routing data (e.g. chatId, channelId)
     */
    public void saveContext(
            final String conversationId, final String channelName, final Map<String, String> routingData) {
        Assert.hasText(conversationId, "conversationId cannot be blank");
        Assert.hasText(channelName, "channelName cannot be blank");
        try {
            final String json = objectMapper.writeValueAsString(routingData != null ? routingData : Map.of());
            queryRepository.upsert(conversationId, channelName, json, Instant.now());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to save channel routing context for conversation " + conversationId, e);
        }
    }

    /**
     * Retrieves the routing context for the given conversation.
     *
     * @param conversationId conversation to look up; returns empty if {@code null}
     * @return routing context, or empty if no context has been saved for this conversation
     */
    public Optional<RoutingContext> getContext(final String conversationId) {
        if (conversationId == null) {
            return Optional.empty();
        }
        return repository.findById(conversationId).map(entity -> {
            try {
                final Map<String, String> data =
                        objectMapper.readValue(entity.getRoutingData(), new TypeReference<Map<String, String>>() {});
                return new RoutingContext(entity.getChannelName(), data);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to deserialize routing data for conversation " + conversationId, e);
            }
        });
    }
}
