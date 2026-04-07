package ai.javaclaw.channels.discord;

import static java.util.Optional.ofNullable;
import static java.util.regex.Pattern.quote;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelContextService;
import ai.javaclaw.channels.ChannelRegistry;
import ai.javaclaw.channels.RoutingContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscordChannel extends ListenerAdapter implements Channel {

    private static final Logger log = LoggerFactory.getLogger(DiscordChannel.class);

    private final String allowedUserId;
    private final Agent agent;
    private final ChannelContextService channelContextService;
    private final ConcurrentHashMap<String, MessageChannel> channelCache = new ConcurrentHashMap<>();

    public DiscordChannel(
            final String allowedUserId,
            final Agent agent,
            final ChannelRegistry channelRegistry,
            final ChannelContextService channelContextService) {
        this.allowedUserId = normalizeUserId(allowedUserId);
        this.agent = agent;
        this.channelContextService = channelContextService;
        channelRegistry.registerChannel(this);
        log.info("Started Discord integration");
    }

    @Override
    public void onMessageReceived(@NotNull final MessageReceivedEvent event) {
        if (!shouldHandle(event)) {
            return;
        }

        final String userId = normalizeUserId(event.getAuthor().getId());
        final MessageChannel channel = event.getChannel();
        final String content = normalizeText(event.getJDA(), event.getMessage(), event.isFromGuild());

        if (content == null) {
            return;
        }

        if (!isAllowedUser(userId)) {
            log.warn("Ignoring Discord message from unauthorized user '{}'", userId);
            reply(channel, "I'm sorry, I don't accept instructions from you.");
            return;
        }

        final String conversationId = getConversationId(channel.getId());
        channelCache.put(channel.getId(), channel);
        channelContextService.saveContext(conversationId, getName(), Map.of("channelId", channel.getId()));

        final String response = agent.respondTo(conversationId, content);
        reply(channel, response);
    }

    @Override
    public void sendMessage(final RoutingContext routingContext, final String message) {
        final String channelId = routingContext.get("channelId");
        final MessageChannel channel = channelId == null ? null : channelCache.get(channelId);
        if (channel == null) {
            log.error("No Discord channel cached for id '{}', cannot send message", channelId);
            return;
        }
        reply(channel, message);
    }

    private boolean shouldHandle(final MessageReceivedEvent event) {
        final User author = event.getAuthor();
        if (author.isBot() || event.isWebhookMessage()) {
            return false;
        }
        return event.isFromType(ChannelType.PRIVATE)
                || event.getMessage().getMentions().isMentioned(event.getJDA().getSelfUser());
    }

    private boolean isAllowedUser(final String userId) {
        return userId != null && userId.equalsIgnoreCase(allowedUserId);
    }

    private static void reply(final MessageChannel channel, final String text) {
        channel.sendMessage(text).queue();
    }

    private static String normalizeText(final JDA jda, final Message message, final boolean guildMessage) {
        String content = message.getContentRaw();
        if (content == null) {
            return null;
        }
        if (guildMessage) {
            final String mention =
                    ofNullable(jda.getSelfUser()).map(User::getAsMention).orElse("");
            content = content.replaceFirst("^\\s*" + quote(mention) + "\\s*", "");
        }
        content = content.trim();
        return content.isBlank() ? null : content;
    }

    private static String getConversationId(final String channelId) {
        return "discord-" + channelId;
    }

    private static String normalizeUserId(final String userId) {
        if (userId == null) {
            return null;
        }
        String normalized = userId.trim();
        if (normalized.startsWith("<@") && normalized.endsWith(">")) {
            normalized = normalized.substring(2, normalized.length() - 1);
            if (normalized.startsWith("!")) {
                normalized = normalized.substring(1);
            }
        }
        return normalized.isBlank() ? null : normalized;
    }
}
