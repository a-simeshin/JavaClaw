package ai.javaclaw.conversations;

import ai.javaclaw.persistence.api.ConversationQueryRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Ensures a {@code conversations} row exists for a given ID before any
 * {@code spring_ai_chat_memory} INSERT is attempted.
 *
 * <p>Without this guard the FK constraint
 * {@code fk_chat_memory_conversation_id} rejects the first message for every
 * new conversation, because the agent writes to chat memory before the
 * conversation row exists.
 */
@Service
public class ConversationEnsurer {

    private final ConversationRepository repository;
    private final ConversationQueryRepository queryRepository;

    public ConversationEnsurer(
            final ConversationRepository repository, final ConversationQueryRepository queryRepository) {
        this.repository = repository;
        this.queryRepository = queryRepository;
    }

    /** Preview length for the auto-derived conversation title. */
    private static final int TITLE_MAX_LENGTH = 80;

    /**
     * Creates the conversation row if it does not already exist (no user association).
     *
     * @param conversationId the conversation ID — must not be blank
     */
    @Transactional
    public void ensureExists(final String conversationId) {
        Assert.hasText(conversationId, "conversationId must not be blank");
        if (!repository.existsById(conversationId)) {
            repository.save(Conversation.newWithId(conversationId));
        }
    }

    /**
     * Creates the conversation row if it does not already exist, associating it
     * with the given user.
     *
     * @param conversationId the conversation ID — must not be blank
     * @param userId         the owning user's database ID — must not be blank
     */
    @Transactional
    public void ensureExistsForUser(final String conversationId, final String userId) {
        Assert.hasText(conversationId, "conversationId must not be blank");
        Assert.hasText(userId, "userId must not be blank");
        if (!repository.existsById(conversationId)) {
            repository.save(Conversation.newForUser(conversationId, userId));
        }
    }

    /**
     * Bumps {@code updated_at} and, when the row has no title yet, stores a
     * truncated preview of {@code userContent} as the conversation title.
     *
     * <p>Never overwrites an existing title — the first user message wins.
     * Called from the chat send path so the conversation list in the UI
     * reflects the latest activity and can be sorted by recency.
     */
    @Transactional
    public void touch(final String conversationId, final String userContent) {
        Assert.hasText(conversationId, "conversationId must not be blank");
        queryRepository.touchTitleIfMissing(conversationId, truncate(userContent));
    }

    private static String truncate(final String text) {
        final String single = StringUtils.normalizeSpace(text);
        if (StringUtils.isEmpty(single)) {
            return null;
        }
        return StringUtils.abbreviate(single, TITLE_MAX_LENGTH);
    }
}
