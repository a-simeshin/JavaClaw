package ai.javaclaw.conversations;

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

    public ConversationEnsurer(final ConversationRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates the conversation row if it does not already exist.
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
}
