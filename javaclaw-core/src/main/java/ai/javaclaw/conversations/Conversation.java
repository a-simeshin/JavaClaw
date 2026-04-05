package ai.javaclaw.conversations;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Conversation metadata row in the {@code conversations} table.
 *
 * <p>The {@code id} is always provided externally (e.g. {@code "web-<uuid>"}), so we
 * implement {@link Persistable} and mark {@link #isNew()} as {@code true} to force
 * Spring Data JDBC to always attempt an INSERT rather than an UPDATE.
 * The {@link ConversationEnsurer} guards against duplicate inserts with an
 * {@code existsById} check before calling {@code save}.
 */
@Table("conversations")
public record Conversation(
        @Id String id,
        @Column("user_id") String userId,
        @Column("title") String title,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt)
        implements Persistable<String> {

    /** Creates a minimal conversation row with only the ID set. */
    public static Conversation newWithId(final String id) {
        final Instant now = Instant.now();
        return new Conversation(id, null, null, now, now);
    }

    @Override
    public String getId() {
        return id;
    }

    /**
     * Always {@code true} — IDs are externally generated, so Spring Data JDBC
     * must always issue an INSERT (never an UPDATE-based upsert).
     */
    @Override
    @Transient
    public boolean isNew() {
        return true;
    }
}
