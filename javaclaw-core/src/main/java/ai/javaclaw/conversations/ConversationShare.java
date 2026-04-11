package ai.javaclaw.conversations;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A share grant allowing another user to access a conversation.
 * Permission can be READ (view only) or WRITE (view + send messages).
 */
@Table("conversation_shares")
public record ConversationShare(
        @Id Long id,
        @Column("conversation_id") String conversationId,
        @Column("shared_with") String sharedWith,
        @Column("permission") String permission,
        @Column("shared_by") String sharedBy,
        @Column("created_at") Instant createdAt) {

    public static final String PERMISSION_READ = "READ";
    public static final String PERMISSION_WRITE = "WRITE";

    /** Creates a new share grant. */
    public static ConversationShare create(
            String conversationId, String sharedWith, String permission, String sharedBy) {
        return new ConversationShare(null, conversationId, sharedWith, permission, sharedBy, Instant.now());
    }

    public boolean isWritable() {
        return PERMISSION_WRITE.equalsIgnoreCase(permission);
    }
}
