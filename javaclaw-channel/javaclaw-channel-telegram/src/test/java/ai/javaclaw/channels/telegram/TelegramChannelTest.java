package ai.javaclaw.channels.telegram;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.channels.ChannelContextService;
import ai.javaclaw.channels.ChannelRegistry;
import ai.javaclaw.channels.RoutingContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@ExtendWith(MockitoExtension.class)
class TelegramChannelTest {

    @Mock
    private TelegramClient telegramClient;

    @Mock
    private Agent agent;

    @Mock
    private ChannelContextService channelContextService;

    // -----------------------------------------------------------------------
    // Ignored updates
    // -----------------------------------------------------------------------

    @Test
    void ignoresUpdatesWithoutMessage() {
        TelegramChannel channel = channel("allowed_user");
        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(false);

        channel.consume(update);

        verifyNoInteractions(agent, telegramClient);
    }

    @Test
    void ignoresUpdatesWithoutText() {
        TelegramChannel channel = channel("allowed_user");
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(false);

        channel.consume(update);

        verifyNoInteractions(agent, telegramClient);
    }

    @Test
    void ignoresMessagesFromNullUsername() throws TelegramApiException {
        TelegramChannel channel = channel("allowed_user");
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getFrom()).thenReturn(null);
        when(message.getChatId()).thenReturn(1L);

        channel.consume(update);

        verifyNoInteractions(agent);
        verify(telegramClient)
                .execute(argThat((SendMessage msg) -> msg.getText().contains("sorry")));
    }

    @Test
    void ignoresMessagesFromUnauthorizedUser() throws TelegramApiException {
        TelegramChannel channel = channel("allowed_user");

        channel.consume(updateFromUnknownUser("other_user"));

        verify(agent, never()).respondTo(anyString(), anyString());
        verify(telegramClient)
                .execute(argThat((SendMessage msg) -> msg.getText().contains("sorry")));
    }

    // -----------------------------------------------------------------------
    // Username matching
    // -----------------------------------------------------------------------

    @Test
    void usernameMatchingIsCaseInsensitive() {
        TelegramChannel channel = channel("Allowed_User");
        when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

        channel.consume(updateFrom("allowed_user", "hello", 42L, null));

        verify(agent).respondTo(anyString(), anyString());
    }

    @Test
    void stripsLeadingAtFromConfiguredUsername() {
        TelegramChannel channel = channel("@Allowed_User");
        when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

        channel.consume(updateFrom("allowed_user", "hello", 42L, null));

        verify(agent).respondTo(anyString(), anyString());
    }

    @Test
    void stripsLeadingAtFromIncomingUsername() {
        TelegramChannel channel = channel("allowed_user");
        when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

        channel.consume(updateFrom("@allowed_user", "hello", 42L, null));

        verify(agent).respondTo(anyString(), anyString());
    }

    // -----------------------------------------------------------------------
    // Conversation ID and SendMessage
    // -----------------------------------------------------------------------

    @Test
    void usesChannelChatIdAsConversationId() {
        TelegramChannel channel = channel("allowed_user");
        when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

        channel.consume(updateFrom("allowed_user", "hello", 42L, null));

        verify(agent).respondTo(eq("telegram-42"), eq("hello"));
    }

    @Test
    void includesMessageThreadIdInConversationId() {
        TelegramChannel channel = channel("allowed_user");
        when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

        channel.consume(updateFrom("allowed_user", "hello", 42L, 567));

        verify(agent).respondTo(eq("telegram-42-567"), eq("hello"));
    }

    @Test
    void sendsAgentResponseToCorrectChatId() throws TelegramApiException {
        TelegramChannel channel = channel("allowed_user");
        when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

        channel.consume(updateFrom("allowed_user", "hello", 42L, null));

        verify(telegramClient)
                .execute(argThat((SendMessage msg) -> "42".equals(msg.getChatId()) && "hi".equals(msg.getText())));
    }

    @Test
    void passesMessageThreadIdToSendMessage() throws TelegramApiException {
        TelegramChannel channel = channel("allowed_user");
        when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

        channel.consume(updateFrom("allowed_user", "hello", 42L, 567));

        verify(telegramClient)
                .execute(argThat((SendMessage msg) -> Integer.valueOf(567).equals(msg.getMessageThreadId())));
    }

    @Test
    void doesNotSetMessageThreadIdWhenAbsent() throws TelegramApiException {
        TelegramChannel channel = channel("allowed_user");
        when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

        channel.consume(updateFrom("allowed_user", "hello", 42L, null));

        verify(telegramClient).execute(argThat((SendMessage msg) -> msg.getMessageThreadId() == null));
    }

    // -----------------------------------------------------------------------
    // Routing context saved on consume
    // -----------------------------------------------------------------------

    @Test
    void savesRoutingContextOnConsume() throws TelegramApiException {
        TelegramChannel channel = channel("allowed_user");
        when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

        channel.consume(updateFrom("allowed_user", "hello", 42L, null));

        verify(channelContextService)
                .saveContext(eq("telegram-42"), anyString(), argThat(data -> "42".equals(data.get("chatId"))));
    }

    // -----------------------------------------------------------------------
    // sendMessage via RoutingContext
    // -----------------------------------------------------------------------

    @Test
    void sendMessageDoesNothingWhenNoChatIdInRoutingContext() {
        TelegramChannel channel = channel("allowed_user");

        channel.sendMessage(new RoutingContext("TelegramChannel", Map.of()), "hello");

        verifyNoInteractions(telegramClient);
    }

    @Test
    void sendMessageDeliversViaChatIdFromRoutingContext() throws TelegramApiException {
        TelegramChannel channel = channel("allowed_user");

        channel.sendMessage(new RoutingContext("TelegramChannel", Map.of("chatId", "99")), "notification");

        verify(telegramClient)
                .execute(argThat(
                        (SendMessage msg) -> "99".equals(msg.getChatId()) && "notification".equals(msg.getText())));
    }

    // -----------------------------------------------------------------------
    // Message formatting (Markdown → HTML)
    // -----------------------------------------------------------------------

    @Test
    void sendMessageConvertsMarkdownToTelegramHtml() throws TelegramApiException {
        TelegramChannel channel = channel("allowed_user");
        when(agent.respondTo(anyString(), anyString()))
                .thenReturn("Here is **bold** text and a [link](https://example.com)");
        channel.consume(updateFrom("allowed_user", "hello", 42L, 567));
        verify(telegramClient)
                .execute(argThat((SendMessage msg) -> ParseMode.HTML.equals(msg.getParseMode())
                        && "Here is <strong>bold</strong> text and a <a href=\"https://example.com\">link</a>"
                                .equals(msg.getText())));
    }

    @Test
    void sendMessageFallbacksToSendingRawTextWhenFailingToSendHtml() throws TelegramApiException {
        TelegramChannel channel = channel("allowed_user");
        when(agent.respondTo(anyString(), anyString()))
                .thenReturn("Here is **bold** text and an image: ![An example image](/assets/images/clawrunr.png)");
        when(telegramClient.execute(argThat((SendMessage m) -> ParseMode.HTML.equals(m.getParseMode()))))
                .thenThrow(new TelegramApiException("Invalid HTML"));
        channel.consume(updateFrom("allowed_user", "hello", 42L, 567));
        verify(telegramClient)
                .execute(
                        argThat(
                                (SendMessage msg) -> msg.getParseMode() == null
                                        && msg.getText()
                                                .equals(
                                                        "Here is **bold** text and an image: ![An example image](/assets/images/clawrunr.png)")));
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private TelegramChannel channel(String allowedUsername) {
        return new TelegramChannel(
                "token", allowedUsername, telegramClient, agent, new ChannelRegistry(), channelContextService);
    }

    private Update updateFromUnknownUser(String username) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getChatId()).thenReturn(1L);
        when(message.getFrom()).thenReturn(user);
        when(user.getUserName()).thenReturn(username);
        return update;
    }

    private Update updateFrom(String username, String text, long chatId, Integer messageThreadId) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getMessageThreadId()).thenReturn(messageThreadId);
        when(message.getFrom()).thenReturn(user);
        when(user.getUserName()).thenReturn(username);

        return update;
    }
}
