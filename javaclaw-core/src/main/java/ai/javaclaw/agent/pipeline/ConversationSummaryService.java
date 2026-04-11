package ai.javaclaw.agent.pipeline;

import java.util.ArrayList;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for creating and retrieving conversation summaries.
 *
 * <p>When the {@link TurnBoundaryWindower} drops older turns to fit the token budget,
 * this service summarizes those turns using the LLM and stores the summary in the database.
 * On subsequent requests, the summary is injected into the system prompt so the model
 * retains awareness of earlier conversation context.
 *
 * <p>Fix #20_sum: uses hierarchical chunked summarization to avoid HTTP 400 on large batches.
 * Fix #17: {@code messagesCovered} is cumulative across summary merges.
 * Fix #18: upsert is wrapped in {@code REQUIRES_NEW} transaction to prevent race conditions.
 */
@Service
public class ConversationSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationSummaryService.class);

    static final String SUMMARIZATION_PROMPT = "Summarize the following conversation messages concisely. "
            + "Preserve key facts, decisions, code snippets mentioned, and action items. "
            + "Keep the summary under 500 words. Respond with the summary only, no preamble.";

    static final String MERGE_SUMMARY_PROMPT = "You are given several partial conversation summaries. "
            + "Merge them into one concise summary under 600 words. "
            + "Preserve all key facts, decisions, code snippets, and action items. "
            + "Respond with the merged summary only, no preamble.";

    /** Maximum estimated tokens per summarization chunk (fix #20_sum). */
    private static final int MAX_CHUNK_TOKENS = 80_000;

    private final ConversationSummaryRepository summaryRepository;
    private final ChatModel chatModel;
    private final TokenEstimator tokenEstimator;

    public ConversationSummaryService(
            final ConversationSummaryRepository summaryRepository,
            final ChatModel chatModel,
            final TokenEstimator tokenEstimator) {
        this.summaryRepository = summaryRepository;
        this.chatModel = chatModel;
        this.tokenEstimator = tokenEstimator;
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
     * <p>Fix #17: {@code messagesCovered} accumulates across calls (previous + current batch).
     * Fix #18: persistence is wrapped in {@code REQUIRES_NEW} transaction.
     * Fix #20_sum: large batches are split into chunks before LLM calls.
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

            // Fix #17: compute cumulative counter from the existing record
            final int previousCovered = summaryRepository
                    .findByConversationId(conversationId)
                    .map(ConversationSummary::messagesCovered)
                    .orElse(0);
            final int totalCovered = previousCovered + droppedMessages.size();

            final String newSummary = callLlmForSummary(existingSummary, droppedMessages);

            if (newSummary != null && !newSummary.isBlank()) {
                // Fix #18: atomic upsert in its own transaction
                persistSummary(conversationId, newSummary, totalCovered);
                log.debug(
                        "Saved conversation summary for {} ({} messages summarized, total covered: {})",
                        conversationId,
                        droppedMessages.size(),
                        totalCovered);
            }
        } catch (final Exception e) {
            log.warn("Failed to summarize dropped messages for conversation {}: {}", conversationId, e.getMessage());
        }
    }

    /**
     * Calls the LLM to generate a summary of the dropped messages, optionally merging with an
     * existing summary. Uses hierarchical chunking when the message batch exceeds token limits.
     *
     * <p>Fix #20_sum: splits large batches into chunks of at most {@value #MAX_CHUNK_TOKENS}
     * tokens, summarizes each chunk separately, then merges all partial summaries in a final call.
     */
    @Nullable
    private String callLlmForSummary(@Nullable final String existingSummary, final List<Message> droppedMessages) {
        final List<List<Message>> chunks = splitByTokenBudget(droppedMessages, MAX_CHUNK_TOKENS);

        if (chunks.size() == 1) {
            // Fast path: single chunk — same as before
            return summarizeChunk(existingSummary, chunks.get(0));
        }

        // Hierarchical reduce: summarize each chunk separately, then merge
        final List<String> partialSummaries = new ArrayList<>();
        for (final List<Message> chunk : chunks) {
            final String partial = summarizeChunk(null, chunk);
            if (partial != null && !partial.isBlank()) {
                partialSummaries.add(partial);
            }
        }

        if (partialSummaries.isEmpty()) {
            return existingSummary;
        }

        // Combine existing summary + all partial summaries, then merge into one
        final String mergedContext = existingSummary != null
                ? existingSummary + "\n\n" + String.join("\n\n", partialSummaries)
                : String.join("\n\n", partialSummaries);
        return summarizeMerge(mergedContext);
    }

    /**
     * Splits a message list into chunks whose estimated token count does not exceed
     * {@code maxTokens}. Each message is placed in the smallest chunk that still fits;
     * messages that alone exceed the budget start a new chunk.
     */
    private List<List<Message>> splitByTokenBudget(final List<Message> messages, final int maxTokens) {
        final List<List<Message>> chunks = new ArrayList<>();
        List<Message> current = new ArrayList<>();
        int currentTokens = 0;

        for (final Message m : messages) {
            final int tokens = tokenEstimator.estimate(m);
            if (currentTokens + tokens > maxTokens && !current.isEmpty()) {
                chunks.add(new ArrayList<>(current));
                current = new ArrayList<>();
                currentTokens = 0;
            }
            current.add(m);
            currentTokens += tokens;
        }

        if (!current.isEmpty()) {
            chunks.add(current);
        }

        return chunks.isEmpty() ? List.of(messages) : chunks;
    }

    /**
     * Summarizes a single chunk of messages, optionally prepending an existing summary.
     *
     * @implNote Must run on a blocking executor; do not call from Reactor scheduler threads.
     */
    @Nullable
    private String summarizeChunk(@Nullable final String existingSummary, final List<Message> chunk) {
        final StringBuilder content = new StringBuilder();

        if (existingSummary != null && !existingSummary.isBlank()) {
            content.append("Previous conversation summary:\n")
                    .append(existingSummary)
                    .append("\n\n");
        }

        content.append("New messages to incorporate into the summary:\n");
        for (final Message msg : chunk) {
            final String role =
                    msg instanceof UserMessage ? "User" : msg instanceof AssistantMessage ? "Assistant" : "System";
            final String text = msg.getText();
            if (text != null && !text.isBlank()) {
                content.append(role).append(": ").append(truncate(text, 2000)).append("\n");
            }
        }

        final Prompt prompt =
                new Prompt(List.of(new SystemMessage(SUMMARIZATION_PROMPT), new UserMessage(content.toString())));
        return extractText(chatModel.call(prompt));
    }

    /**
     * Consolidates multiple partial summaries into a single coherent summary via one LLM call.
     */
    @Nullable
    private String summarizeMerge(final String mergedContext) {
        final Prompt prompt =
                new Prompt(List.of(new SystemMessage(MERGE_SUMMARY_PROMPT), new UserMessage(mergedContext)));
        return extractText(chatModel.call(prompt));
    }

    /**
     * Atomically upserts a conversation summary.
     *
     * <p>Fix #18: runs in its own transaction ({@code REQUIRES_NEW}) so the delete+save is
     * isolated from the surrounding {@code @Async} context (which has no transaction).
     * This prevents race conditions where a concurrent read sees a deleted-but-not-yet-saved state.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistSummary(final String conversationId, final String summary, final int totalCovered) {
        summaryRepository.deleteByConversationId(conversationId);
        summaryRepository.save(ConversationSummary.create(conversationId, summary, totalCovered));
    }

    @Nullable
    private static String extractText(@Nullable final ChatResponse response) {
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
