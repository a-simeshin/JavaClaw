package ai.javaclaw.persistence.api;

import java.time.Instant;
import java.util.List;

/**
 * Dialect-sensitive queries around conversations and their chat memory rows.
 *
 * <p>Covers two concerns:
 * <ul>
 *   <li>Reading a paginated, deterministically-ordered message slice from
 *       {@code SPRING_AI_CHAT_MEMORY}. Postgres uses {@code ctid} as the final
 *       tiebreaker; SQLite uses {@code rowid} and drops the double-quoting
 *       around the {@code timestamp} column.</li>
 *   <li>Touching a {@code conversations} row ({@code updated_at = now()} /
 *       {@code datetime('now')}) and initialising its title if still NULL.</li>
 * </ul>
 */
public interface ConversationQueryRepository {

    /**
     * Returns a paginated, oldest-first slice of chat memory rows for a conversation.
     * Ordering is stable across repeated calls thanks to a dialect-specific physical
     * tiebreaker ({@code ctid} on Postgres, {@code rowid} on SQLite).
     */
    List<ChatMessageRow> findMessagesOrdered(String conversationId, int offset, int limit);

    /**
     * Bumps {@code updated_at} for the given conversation and initialises its title
     * when still NULL. Returns the number of rows affected (0 if the conversation
     * does not exist).
     */
    int touchTitleIfMissing(String conversationId, String title);

    /** Raw row from {@code SPRING_AI_CHAT_MEMORY}. */
    record ChatMessageRow(String content, String type, Instant createdAt) {}
}
