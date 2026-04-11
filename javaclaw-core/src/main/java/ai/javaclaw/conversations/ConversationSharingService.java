package ai.javaclaw.conversations;

import ai.javaclaw.users.Permission;
import ai.javaclaw.users.PermissionService;
import java.util.List;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Service for sharing conversations between users.
 * Supports READ (view only) and WRITE (view + send messages) permissions.
 * Admins with {@link Permission#CONVERSATION_ACCESS_ALL} bypass ownership checks.
 */
@Service
public class ConversationSharingService {

    private final ConversationShareRepository shareRepository;
    private final ConversationRepository conversationRepository;
    private final PermissionService permissionService;

    public ConversationSharingService(
            ConversationShareRepository shareRepository,
            ConversationRepository conversationRepository,
            PermissionService permissionService) {
        this.shareRepository = shareRepository;
        this.conversationRepository = conversationRepository;
        this.permissionService = permissionService;
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

    /**
     * Check if a user has access to a conversation, with optional admin bypass.
     *
     * @param conversationId the conversation ID
     * @param userId         the user's database ID
     * @param username       the authenticated username (for permission check); if null, no admin bypass
     * @return true if user has access (as owner, shared, or admin with CONVERSATION_ACCESS_ALL)
     */
    public boolean hasAccess(String conversationId, String userId, @Nullable String username) {
        if (username != null && isAdmin(username)) {
            return true;
        }
        return hasAccess(conversationId, userId);
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

    /**
     * Check if user has write access, with optional admin bypass.
     * Admins with CONVERSATION_ACCESS_ALL always have write access.
     */
    public boolean hasWriteAccess(String conversationId, String userId, @Nullable String username) {
        if (username != null && isAdmin(username)) {
            return true;
        }
        return hasWriteAccess(conversationId, userId);
    }

    /**
     * List all shares for a conversation. Admins with CONVERSATION_ACCESS_ALL can view any conversation's shares.
     *
     * @param conversationId the conversation
     * @param ownerId        the caller's user ID
     * @param username       the authenticated username (for permission check); if null, owner-only
     */
    public List<ConversationShare> listShares(String conversationId, String ownerId, @Nullable String username) {
        if (username != null && isAdmin(username)) {
            return shareRepository.findByConversationId(conversationId);
        }
        return listShares(conversationId, ownerId);
    }

    /**
     * Remove a share grant. Admins with CONVERSATION_ACCESS_ALL can unshare from any conversation.
     */
    public void unshare(String conversationId, String ownerId, String targetUserId, @Nullable String username) {
        if (username != null && isAdmin(username)) {
            shareRepository.deleteByConversationIdAndSharedWith(conversationId, targetUserId);
            return;
        }
        unshare(conversationId, ownerId, targetUserId);
    }

    /** Get all conversation IDs shared with a specific user. */
    public List<String> getSharedConversationIds(String userId) {
        return shareRepository.findBySharedWith(userId).stream()
                .map(ConversationShare::conversationId)
                .toList();
    }

    /**
     * Check if a user has the CONVERSATION_ACCESS_ALL permission (admin access).
     */
    public boolean isAdmin(String username) {
        return permissionService.userHasPermission(username, Permission.CONVERSATION_ACCESS_ALL);
    }
}
