package ai.javaclaw.channels;

public interface Channel {

    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Delivers {@code message} to the user identified by {@code routingContext}.
     * The routing context carries channel-specific identifiers (e.g. chatId for Telegram,
     * channelId for Discord, conversationId for Web Chat) that allow the channel to route
     * the message to the correct recipient.
     */
    void sendMessage(RoutingContext routingContext, String message);
}
