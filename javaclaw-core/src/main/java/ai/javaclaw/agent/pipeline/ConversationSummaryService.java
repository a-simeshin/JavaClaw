package ai.javaclaw.agent.pipeline;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for creating and retrieving conversation summaries.
 *
 * <p>When the {@link TurnBoundaryWindower} drops older turns to fit the token budget,
 * this service summarizes those turns using the LLM and stores the summary in the database.
 * On subsequent requests, the summary is injected into the system prompt so the model
 * retains awareness of earlier conversation context.
 */
@Service
public class ConversationSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationSummaryService.class);

    static final String SUMMARIZATION_PROMPT = "Summarize the following conversation messages concisely. "
            + "Preserve key facts, decisions, code snippets mentioned, and action items. "
            + "Keep the summary under 500 words. Respond with the summary only, no preamble.";

    private final ConversationSummaryRepository summaryRepository;
    private final ChatModel chatModel;

    public ConversationSummaryService(
            final ConversationSummaryRepository summaryRepository, final ChatModel chatModel) {
        this.summaryRepository = summaryRepository;
        this.chatModel = chatModel;
    }

    /**
     * Returns the latest summary for a conversation, if one exists.
     *
     * @param conversationId conversation identifier
     * @return summary text or {@code null} if no summary exists
     */
    @Nullable
    public String getExistingSummary(final String conversationId) {
        return summaryRepository
                .findLatestByConversationId(conversationId)
                .map(ConversationSummary::summaryText)
                .orElse(null);
    }

    /**
     * Asynchronously summarizes dropped messages and stores the result.
     * Merges with any existing summary for the conversation.
     *
     * @param conversationId conversation identifier
     * @param droppedMessages messages that were dropped during windowing
     */
    @Async
    public void summarizeDroppedMessages(final String conversationId, final List<Message> droppedMessages) {
        if (droppedMessages == null || droppedMessages.isEmpty()) {
            return;
        }
        try {
            final String existingSummary = getExistingSummary(conversationId);
            final String newSummary = callLlmForSummary(existingSummary, droppedMessages);

            if (newSummary != null && !newSummary.isBlank()) {
                summaryRepository.deleteByConversationId(conversationId);
                summaryRepository.save(ConversationSummary.create(conversationId, newSummary, droppedMessages.size()));
                log.debug(
                        "Saved conversation summary for {} ({} messages summarized)",
                        conversationId,
                        droppedMessages.size());
            }
        } catch (final Exception e) {
            log.warn("Failed to summarize dropped messages for conversation {}: {}", conversationId, e.getMessage());
        }
    }

    /**
     * Calls the LLM to generate a summary of the dropped messages,
     * optionally merging with an existing summary.
     */
    @Nullable
    private String callLlmForSummary(@Nullable final String existingSummary, final List<Message> droppedMessages) {
        final StringBuilder content = new StringBuilder();

        if (existingSummary != null && !existingSummary.isBlank()) {
            content.append("Previous conversation summary:\n")
                    .append(existingSummary)
                    .append("\n\n");
        }

        content.append("New messages to incorporate into the summary:\n");
        for (final Message msg : droppedMessages) {
            final String role =
                    msg instanceof UserMessage ? "User" : msg instanceof AssistantMessage ? "Assistant" : "System";
            final String text = msg.getText();
            if (text != null && !text.isBlank()) {
                content.append(role).append(": ").append(truncate(text, 2000)).append("\n");
            }
        }

        final Prompt prompt =
                new Prompt(List.of(new SystemMessage(SUMMARIZATION_PROMPT), new UserMessage(content.toString())));

        final ChatResponse response = chatModel.call(prompt);
        if (response != null
                && response.getResult() != null
                && response.getResult().getOutput() != null) {
            return response.getResult().getOutput().getText();
        }
        return null;
    }

    private static String truncate(final String text, final int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
