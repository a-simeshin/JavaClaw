package ai.javaclaw.conversations;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Service for sharing conversations between users.
 * Supports READ (view only) and WRITE (view + send messages) permissions.
 */
@Service
public class ConversationSharingService {

    private final ConversationShareRepository shareRepository;
    private final ConversationRepository conversationRepository;

    public ConversationSharingService(
            ConversationShareRepository shareRepository, ConversationRepository conversationRepository) {
        this.shareRepository = shareRepository;
        this.conversationRepository = conversationRepository;
    }

    /**
     * Share a conversation with another user.
     *
     * @param conversationId the conversation to share
     * @param ownerId        the owner sharing the conversation
     * @param targetUserId   the user to share with
     * @param permission     READ or WRITE
     * @return the created share
     * @throws IllegalArgumentException if conversation doesn't exist or isn't owned by ownerId
     * @throws IllegalStateException    if already shared with targetUserId
     */
    public ConversationShare share(String conversationId, String ownerId, String targetUserId, String permission) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new IllegalArgumentException("targetUserId must not be blank");
        }
        if (ownerId.equals(targetUserId)) {
            throw new IllegalArgumentException("Cannot share conversation with yourself");
        }
        if (!ConversationShare.PERMISSION_READ.equalsIgnoreCase(permission)
                && !ConversationShare.PERMISSION_WRITE.equalsIgnoreCase(permission)) {
            throw new IllegalArgumentException("Permission must be READ or WRITE");
        }
        if (!conversationRepository.existsByIdAndUserId(conversationId, ownerId)) {
            throw new IllegalArgumentException("Conversation not found or not owned by you");
        }
        if (shareRepository.existsByConversationIdAndSharedWith(conversationId, targetUserId)) {
            throw new IllegalStateException("Conversation already shared with this user");
        }
        return shareRepository.save(
                ConversationShare.create(conversationId, targetUserId, permission.toUpperCase(), ownerId));
    }

    /**
     * Remove a share grant.
     *
     * @param conversationId the conversation
     * @param ownerId        the owner revoking access
     * @param targetUserId   the user to unshare from
     * @throws IllegalArgumentException if conversation isn't owned by ownerId
     */
    public void unshare(String conversationId, String ownerId, String targetUserId) {
        if (!conversationRepository.existsByIdAndUserId(conversationId, ownerId)) {
            throw new IllegalArgumentException("Conversation not found or not owned by you");
        }
        shareRepository.deleteByConversationIdAndSharedWith(conversationId, targetUserId);
    }

    /** List all shares for a conversation (only owner should call this). */
    public List<ConversationShare> listShares(String conversationId, String ownerId) {
        if (!conversationRepository.existsByIdAndUserId(conversationId, ownerId)) {
            throw new IllegalArgumentException("Conversation not found or not owned by you");
        }
        return shareRepository.findByConversationId(conversationId);
    }

    /** Check if a user has access to a conversation (as owner or via share). */
    public boolean hasAccess(String conversationId, String userId) {
        if (conversationRepository.existsByIdAndUserId(conversationId, userId)) {
            return true;
        }
        return shareRepository.existsByConversationIdAndSharedWith(conversationId, userId);
    }

    /** Check if user has write access (owner always has write; shared users need WRITE permission). */
    public boolean hasWriteAccess(String conversationId, String userId) {
        if (conversationRepository.existsByIdAndUserId(conversationId, userId)) {
            return true;
        }
        return shareRepository
                .findByConversationIdAndSharedWith(conversationId, userId)
                .map(ConversationShare::isWritable)
                .orElse(false);
    }

    /** Get all conversation IDs shared with a specific user. */
    public List<String> getSharedConversationIds(String userId) {
        return shareRepository.findBySharedWith(userId).stream()
                .map(ConversationShare::conversationId)
                .toList();
    }
}
