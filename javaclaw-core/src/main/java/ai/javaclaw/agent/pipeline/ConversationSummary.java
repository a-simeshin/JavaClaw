package ai.javaclaw.agent.pipeline;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Persisted summary of older conversation turns that were dropped during context window management.
 *
 * @param id              auto-generated UUID
 * @param conversationId  conversation this summary belongs to
 * @param summaryText     LLM-generated summary of dropped messages
 * @param messagesCovered number of messages that were summarized
 */
@Table("conversation_summaries")
public record ConversationSummary(
        @Id String id,
        @Column("conversation_id") String conversationId,
        @Column("summary_text") String summaryText,
        @Column("messages_covered") int messagesCovered) {

    public static ConversationSummary create(String conversationId, String summaryText, int messagesCovered) {
        return new ConversationSummary(null, conversationId, summaryText, messagesCovered);
    }
}
