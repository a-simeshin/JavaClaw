package ai.javaclaw.delivery;

import org.springframework.context.ApplicationEvent;

/**
 * Spring ApplicationEvent carrying a notification payload for a specific conversation.
 * Used by {@link InMemoryNotificationTransport} for in-process notification delivery.
 */
public class NotificationEvent extends ApplicationEvent {

    private final String conversationId;
    private final String payload;

    public NotificationEvent(Object source, String conversationId, String payload) {
        super(source);
        this.conversationId = conversationId;
        this.payload = payload;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getPayload() {
        return payload;
    }
}
