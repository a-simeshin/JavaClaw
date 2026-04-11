package ai.javaclaw.api.admin.tools;

import ai.javaclaw.ai.memory.AppendableChatMemoryRepository;
import ai.javaclaw.delivery.NotificationTransport;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Agent tool for sending messages/notifications to conversations.
 * Useful for background tasks that need to notify users of progress or results.
 * Messages are both broadcast in real-time (SSE) and persisted in chat memory.
 */
public class MessageTool {

    private static final Logger logger = LoggerFactory.getLogger(MessageTool.class);

    private final NotificationTransport notificationTransport;
    private final AppendableChatMemoryRepository chatMemoryRepository;

    public MessageTool(
            NotificationTransport notificationTransport, AppendableChatMemoryRepository chatMemoryRepository) {
        this.notificationTransport = notificationTransport;
        this.chatMemoryRepository = chatMemoryRepository;
    }

    @Tool(
            description =
                    """
            Sends a message to a specific conversation. The message is delivered in real-time
            via SSE push notification and persisted in chat memory so it is visible on page reload.

            Use this when you need to:
            - Notify a user about task completion or progress from a background job
            - Send a follow-up message to a conversation after the main response
            - Deliver alerts or status updates to a specific conversation

            - conversationId: The ID of the conversation to send the message to.
            - message: The text content of the message to send. Supports markdown formatting.

            Returns confirmation of delivery.
            """)
    public String sendMessage(String conversationId, String message) {
        try {
            if (conversationId == null || conversationId.isBlank()) {
                return "Error: conversationId must not be empty.";
            }
            if (message == null || message.isBlank()) {
                return "Error: message must not be empty.";
            }

            notificationTransport.broadcast(conversationId, message);

            try {
                chatMemoryRepository.appendAll(conversationId, List.of(new AssistantMessage(message)));
            } catch (Exception e) {
                logger.warn("Failed to persist message to chat memory for {}: {}", conversationId, e.getMessage());
            }

            return String.format("Message sent to conversation '%s'.", conversationId);
        } catch (Exception e) {
            logger.error("sendMessage failed for conversationId={}", conversationId, e);
            return "Error: Could not send message. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Sends a message to the current conversation. This is a convenience method
            that broadcasts a notification without requiring the conversationId parameter
            (the agent runtime provides it via context).

            Use this when you want to push an out-of-band notification or status update
            to the user in the current conversation.

            - conversationId: The current conversation ID (provided by the agent context).
            - message: The notification text. Supports markdown.
            - urgent: If true, the message is prefixed with [URGENT] for visibility.

            Returns confirmation of delivery.
            """)
    public String notify(String conversationId, String message, boolean urgent) {
        try {
            if (conversationId == null || conversationId.isBlank()) {
                return "Error: conversationId must not be empty.";
            }
            if (message == null || message.isBlank()) {
                return "Error: message must not be empty.";
            }

            String payload = urgent ? "[URGENT] " + message : message;
            return sendMessage(conversationId, payload);
        } catch (Exception e) {
            logger.error("notify failed for conversationId={}", conversationId, e);
            return "Error: Could not send notification. " + e.getMessage();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private NotificationTransport notificationTransport;
        private AppendableChatMemoryRepository chatMemoryRepository;

        public Builder notificationTransport(NotificationTransport notificationTransport) {
            this.notificationTransport = notificationTransport;
            return this;
        }

        public Builder chatMemoryRepository(AppendableChatMemoryRepository chatMemoryRepository) {
            this.chatMemoryRepository = chatMemoryRepository;
            return this;
        }

        public MessageTool build() {
            return new MessageTool(this.notificationTransport, this.chatMemoryRepository);
        }
    }
}
