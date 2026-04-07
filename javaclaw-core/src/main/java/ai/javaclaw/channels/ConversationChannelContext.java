package ai.javaclaw.channels;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Spring Data JDBC entity mapping to {@code conversation_channel_context}. */
@Table("conversation_channel_context")
public class ConversationChannelContext {

    @Id
    private final String conversationId;

    private final String channelName;

    /** JSON-encoded routing data, e.g. {@code {"chatId":"42","threadId":"7"}}. */
    private final String routingData;

    private final Instant updatedAt;

    public ConversationChannelContext(
            final String conversationId, final String channelName, final String routingData, final Instant updatedAt) {
        this.conversationId = conversationId;
        this.channelName = channelName;
        this.routingData = routingData;
        this.updatedAt = updatedAt;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getRoutingData() {
        return routingData;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
