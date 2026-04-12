package ai.javaclaw.api.admin.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ai.javaclaw.agent.memory.ChatMemory;
import ai.javaclaw.delivery.NotificationTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageToolTest {

    private NotificationTransport notificationTransport;
    private ChatMemory chatMemoryRepository;
    private MessageTool messageTool;

    @BeforeEach
    void setUp() {
        notificationTransport = mock(NotificationTransport.class);
        chatMemoryRepository = mock(ChatMemory.class);
        messageTool = MessageTool.builder()
                .notificationTransport(notificationTransport)
                .chatMemoryRepository(chatMemoryRepository)
                .build();
    }

    @Test
    void sendMessage_broadcastsAndPersists() {
        String result = messageTool.sendMessage("conv-1", "Hello user!");

        assertThat(result).contains("Message sent to conversation 'conv-1'");
        verify(notificationTransport).broadcast("conv-1", "Hello user!");
        verify(chatMemoryRepository).appendAll(eq("conv-1"), any());
    }

    @Test
    void sendMessage_nullConversationId_returnsError() {
        String result = messageTool.sendMessage(null, "Hello");

        assertThat(result).contains("Error: conversationId must not be empty");
        verifyNoInteractions(notificationTransport, chatMemoryRepository);
    }

    @Test
    void sendMessage_blankConversationId_returnsError() {
        String result = messageTool.sendMessage("  ", "Hello");

        assertThat(result).contains("Error: conversationId must not be empty");
        verifyNoInteractions(notificationTransport, chatMemoryRepository);
    }

    @Test
    void sendMessage_nullMessage_returnsError() {
        String result = messageTool.sendMessage("conv-1", null);

        assertThat(result).contains("Error: message must not be empty");
        verifyNoInteractions(notificationTransport, chatMemoryRepository);
    }

    @Test
    void sendMessage_blankMessage_returnsError() {
        String result = messageTool.sendMessage("conv-1", "");

        assertThat(result).contains("Error: message must not be empty");
        verifyNoInteractions(notificationTransport, chatMemoryRepository);
    }

    @Test
    void sendMessage_broadcastThrows_returnsError() {
        doThrow(new RuntimeException("Transport down"))
                .when(notificationTransport)
                .broadcast(any(), any());

        String result = messageTool.sendMessage("conv-1", "Hello");

        assertThat(result).contains("Error: Could not send message").contains("Transport down");
    }

    @Test
    void sendMessage_persistenceFailure_stillSucceeds() {
        doThrow(new RuntimeException("DB error")).when(chatMemoryRepository).appendAll(any(), any());

        String result = messageTool.sendMessage("conv-1", "Hello");

        assertThat(result).contains("Message sent to conversation 'conv-1'");
        verify(notificationTransport).broadcast("conv-1", "Hello");
    }

    @Test
    void notify_sendsNormalMessage() {
        String result = messageTool.notify("conv-2", "Task done", false);

        assertThat(result).contains("Message sent to conversation 'conv-2'");
        verify(notificationTransport).broadcast("conv-2", "Task done");
    }

    @Test
    void notify_urgent_prefixesMessage() {
        String result = messageTool.notify("conv-2", "Server is down", true);

        assertThat(result).contains("Message sent to conversation 'conv-2'");
        verify(notificationTransport).broadcast("conv-2", "[URGENT] Server is down");
    }

    @Test
    void notify_nullConversationId_returnsError() {
        String result = messageTool.notify(null, "Hello", false);

        assertThat(result).contains("Error: conversationId must not be empty");
        verifyNoInteractions(notificationTransport, chatMemoryRepository);
    }

    @Test
    void notify_blankMessage_returnsError() {
        String result = messageTool.notify("conv-1", "  ", true);

        assertThat(result).contains("Error: message must not be empty");
        verifyNoInteractions(notificationTransport, chatMemoryRepository);
    }
}
