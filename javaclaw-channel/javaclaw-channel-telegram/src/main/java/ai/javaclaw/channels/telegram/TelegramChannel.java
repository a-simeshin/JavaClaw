package ai.javaclaw.channels.telegram;

import static java.util.Optional.ofNullable;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelContextService;
import ai.javaclaw.channels.ChannelRegistry;
import ai.javaclaw.channels.RoutingContext;
import java.util.List;
import java.util.Map;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

public class TelegramChannel implements Channel, SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final Parser MARKDOWN_PARSER = Parser.builder().build();
    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder()
            .escapeHtml(true)
            .extensions(List.of(StrikethroughExtension.create()))
            .build();
    private static final Logger log = LoggerFactory.getLogger(TelegramChannel.class);
    private final String botToken;
    private final String allowedUsername;
    private final TelegramClient telegramClient;
    private final Agent agent;
    private final ChannelContextService channelContextService;

    public TelegramChannel(
            String botToken,
            String allowedUsername,
            Agent agent,
            ChannelRegistry channelRegistry,
            ChannelContextService channelContextService) {
        this(
                botToken,
                allowedUsername,
                new OkHttpTelegramClient(botToken),
                agent,
                channelRegistry,
                channelContextService);
    }

    TelegramChannel(
            String botToken,
            String allowedUsername,
            TelegramClient telegramClient,
            Agent agent,
            ChannelRegistry channelRegistry,
            ChannelContextService channelContextService) {
        this.botToken = botToken;
        this.allowedUsername = normalizeUsername(allowedUsername);
        this.telegramClient = telegramClient;
        this.agent = agent;
        this.channelContextService = channelContextService;
        channelRegistry.registerChannel(this);
        log.info("Started Telegram integration");
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(final Update update) {
        if (!(update.hasMessage() && update.getMessage().hasText())) return;

        final Message requestMessage = update.getMessage();
        final String userName = requestMessage.getFrom() == null
                ? null
                : requestMessage.getFrom().getUserName();
        if (!isAllowedUser(userName)) {
            log.warn("Ignoring Telegram message from unauthorized username '{}'", userName);
            sendTelegram(
                    requestMessage.getChatId(),
                    requestMessage.getMessageThreadId(),
                    "I'm sorry, I don't accept instructions from you.");
            return;
        }

        final String messageText = requestMessage.getText();
        final long chatId = requestMessage.getChatId();
        final Integer messageThreadId = requestMessage.getMessageThreadId();
        final String conversationId = getConversationId(chatId, messageThreadId);

        final Map<String, String> routingData = messageThreadId != null
                ? Map.of("chatId", String.valueOf(chatId), "threadId", String.valueOf(messageThreadId))
                : Map.of("chatId", String.valueOf(chatId));
        channelContextService.saveContext(conversationId, getName(), routingData);

        final String response = agent.respondTo(conversationId, messageText);
        sendTelegram(chatId, messageThreadId, response);
    }

    @Override
    public void sendMessage(final RoutingContext routingContext, final String message) {
        final String chatIdStr = routingContext.get("chatId");
        if (chatIdStr == null) {
            log.error("No chatId in RoutingContext for TelegramChannel, cannot send message");
            return;
        }
        final long chatId = Long.parseLong(chatIdStr);
        final String threadIdStr = routingContext.get("threadId");
        final Integer messageThreadId = threadIdStr == null ? null : Integer.parseInt(threadIdStr);
        sendTelegram(chatId, messageThreadId, message);
    }

    private static String convertMarkdownToTelegramHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Node document = MARKDOWN_PARSER.parse(markdown);
        String html = HTML_RENDERER.render(document);
        return html.replace("<p>", "")
                .replace("</p>", "\n")
                .replaceAll("<h[1-6]>", "<b>")
                .replaceAll("</h[1-6]>", "</b>\n")
                .replace("<li>", "• ")
                .replace("</li>", "\n")
                .replace("<ul>", "")
                .replace("</ul>", "")
                .replace("<ol>", "")
                .replace("</ol>", "")
                .replace("<hr />", "———\n")
                .trim();
    }

    void sendTelegram(final long chatId, final Integer messageThreadId, final String message) {
        final String formattedHtmlMessage = convertMarkdownToTelegramHtml(message);
        SendMessage htmlMessage = SendMessage.builder()
                .chatId(chatId)
                .messageThreadId(messageThreadId)
                .text(formattedHtmlMessage)
                .parseMode(ParseMode.HTML)
                .build();
        try {
            telegramClient.execute(htmlMessage);
        } catch (TelegramApiException e) {
            log.warn("Failed to send HTML parsed message, falling back to raw text.", e);
            SendMessage fallback = SendMessage.builder()
                    .chatId(chatId)
                    .messageThreadId(messageThreadId)
                    .text(message)
                    .build();
            try {
                telegramClient.execute(fallback);
            } catch (TelegramApiException fx) {
                throw new RuntimeException("Failed to send both HTML and fallback messages", fx);
            }
        }
    }

    private boolean isAllowedUser(final String userName) {
        final String normalizedUserName = normalizeUsername(userName);
        return normalizedUserName != null && normalizedUserName.equalsIgnoreCase(allowedUsername);
    }

    private static String normalizeUsername(final String userName) {
        if (userName == null) {
            return null;
        }
        String normalizedUserName = userName.trim();
        if (normalizedUserName.startsWith("@")) {
            normalizedUserName = normalizedUserName.substring(1);
        }
        return normalizedUserName.isBlank() ? null : normalizedUserName;
    }

    private String getConversationId(final long chatId, final Integer messageThreadId) {
        return "telegram-" + chatId
                + ofNullable(messageThreadId).map(i -> "-" + i).orElse("");
    }
}
