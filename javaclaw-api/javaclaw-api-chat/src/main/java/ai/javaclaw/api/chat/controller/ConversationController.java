package ai.javaclaw.api.chat.controller;

import ai.javaclaw.api.chat.controller.dto.ConversationDto;
import ai.javaclaw.api.chat.controller.dto.CreateConversationRequest;
import ai.javaclaw.api.chat.controller.dto.MessageDto;
import ai.javaclaw.api.chat.controller.dto.PageResponse;
import ai.javaclaw.conversations.ConversationEnsurer;
import ai.javaclaw.conversations.ConversationQueryService;
import ai.javaclaw.conversations.ConversationRepository;
import ai.javaclaw.conversations.ConversationShare;
import ai.javaclaw.conversations.ConversationSharingService;
import ai.javaclaw.users.UserResolver;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lists / inspects / deletes conversations.
 *
 * <p>Metadata (title, created_at, updated_at) is read from the {@code
 * conversations} table via {@link ConversationQueryService}; messages come
 * from {@code SPRING_AI_CHAT_MEMORY} with their real row timestamps. The
 * first user message is used as a display-time fallback for an unset title.
 */
@RestController
@AllArgsConstructor
@RequestMapping("/api/conversations")
public class ConversationController {

    /** Max length for auto-derived conversation titles shown in the list. */
    private static final int TITLE_MAX_LENGTH = 80;

    private final ChatMemoryRepository chatMemoryRepository;
    private final ConversationEnsurer conversationEnsurer;
    private final ConversationQueryService queryService;
    private final ConversationRepository conversationRepository;
    private final ConversationSharingService sharingService;
    private final UserResolver userResolver;

    @GetMapping
    public PageResponse<ConversationDto> list(
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size,
            final Principal principal) {
        final String username = principal.getName();
        final String userId = userResolver.resolveUserId(username);
        // Admins with CONVERSATION_ACCESS_ALL see all conversations
        final ConversationQueryService.Page<ConversationQueryService.ConversationSummary> result =
                sharingService.isAdmin(username)
                        ? queryService.listConversations(page, size)
                        : queryService.listConversationsWithShared(userId, page, size);
        final List<ConversationDto> dtos = new ArrayList<>(result.content().size());
        for (ConversationQueryService.ConversationSummary row : result.content()) {
            dtos.add(toDto(row));
        }
        return new PageResponse<>(dtos, result.page(), result.size(), result.total());
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("hasPermission(#id, 'conversation', 'read')")
    public PageResponse<MessageDto> messages(
            @PathVariable final String id,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "50") final int size,
            final Principal principal) {
        // Access check is performed by @PreAuthorize via PermissionResolvers (conversation/read).
        final ConversationQueryService.Page<ConversationQueryService.MessageRow> result =
                queryService.listMessages(id, page, size);
        final List<MessageDto> dtos = new ArrayList<>(result.content().size());
        int index = result.page() * result.size();
        for (ConversationQueryService.MessageRow row : result.content()) {
            dtos.add(new MessageDto(
                    id + "#" + index, roleOf(row.type()), row.content() == null ? "" : row.content(), row.createdAt()));
            index++;
        }
        return new PageResponse<>(dtos, result.page(), result.size(), result.total());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_CONVERSATION_CREATE')")
    public ConversationDto create(
            @Valid @RequestBody(required = false) final CreateConversationRequest request, final Principal principal) {
        final String id =
                request != null && request.id() != null && !request.id().isBlank()
                        ? request.id()
                        : "web-" + UUID.randomUUID();
        final String userId = userResolver.resolveUserId(principal.getName());
        conversationEnsurer.ensureExistsForUser(id, userId);
        final String title =
                request != null && request.title() != null && !request.title().isBlank()
                        ? truncate(request.title())
                        : null;
        if (title != null) {
            conversationEnsurer.touch(id, title);
        }
        final Instant now = Instant.now();
        return new ConversationDto(id, title != null ? title : id, now, now, 0);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'conversation', 'delete')")
    public ResponseEntity<Void> delete(@PathVariable final String id, final Principal principal) {
        // Access check is performed by @PreAuthorize via PermissionResolvers (conversation/delete).
        chatMemoryRepository.deleteByConversationId(id);
        conversationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/share")
    @PreAuthorize("hasPermission(#id, 'conversation', 'share')")
    public ResponseEntity<?> share(
            @PathVariable final String id, @RequestBody final ShareRequest request, final Principal principal) {
        final String ownerId = userResolver.resolveUserId(principal.getName());
        final String targetUserId = userResolver.resolveUserId(request.username());
        final String permission =
                request.permission() != null ? request.permission() : ConversationShare.PERMISSION_READ;
        ConversationShare share = sharingService.share(id, ownerId, targetUserId, permission);
        return ResponseEntity.ok(new ShareResponse(share.conversationId(), request.username(), share.permission()));
    }

    @DeleteMapping("/{id}/share/{username}")
    @PreAuthorize("hasPermission(#id, 'conversation', 'share')")
    public ResponseEntity<Void> unshare(
            @PathVariable final String id, @PathVariable final String username, final Principal principal) {
        final String callerUsername = principal.getName();
        final String ownerId = userResolver.resolveUserId(callerUsername);
        final String targetUserId = userResolver.resolveUserId(username);
        sharingService.unshare(id, ownerId, targetUserId, callerUsername);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/shares")
    @PreAuthorize("hasPermission(#id, 'conversation', 'share')")
    public List<ShareResponse> listShares(@PathVariable final String id, final Principal principal) {
        final String callerUsername = principal.getName();
        final String ownerId = userResolver.resolveUserId(callerUsername);
        return sharingService.listShares(id, ownerId, callerUsername).stream()
                .map(s -> new ShareResponse(s.conversationId(), s.sharedWith(), s.permission()))
                .toList();
    }

    @PutMapping("/{id}/share")
    @PreAuthorize("hasPermission(#id, 'conversation', 'share')")
    public ResponseEntity<?> updateSharePermission(
            @PathVariable final String id, @RequestBody final ShareRequest request, final Principal principal) {
        final String callerUsername = principal.getName();
        final String ownerId = userResolver.resolveUserId(callerUsername);
        final String targetUserId = userResolver.resolveUserId(request.username());
        sharingService.unshare(id, ownerId, targetUserId, callerUsername);
        final String permission =
                request.permission() != null ? request.permission() : ConversationShare.PERMISSION_READ;
        ConversationShare share = sharingService.share(id, ownerId, targetUserId, permission);
        return ResponseEntity.ok(new ShareResponse(share.conversationId(), request.username(), share.permission()));
    }

    public record ShareRequest(String username, String permission) {}

    public record ShareResponse(String conversationId, String username, String permission) {}

    private static ConversationDto toDto(final ConversationQueryService.ConversationSummary row) {
        // Title priority: persisted title → first user message preview → id.
        String title = row.title();
        if (title == null || title.isBlank()) {
            title = row.firstUserMessage() != null && !row.firstUserMessage().isBlank()
                    ? truncate(row.firstUserMessage())
                    : row.id();
        }
        return new ConversationDto(row.id(), title, row.createdAt(), row.updatedAt(), row.messageCount());
    }

    private static String truncate(final String text) {
        final String single = StringUtils.normalizeSpace(text);
        if (StringUtils.isEmpty(single)) {
            return null;
        }
        return StringUtils.abbreviate(single, TITLE_MAX_LENGTH);
    }

    /** Maps Spring AI {@code MessageType} enum names to the API role string. */
    private static String roleOf(final String type) {
        if (type == null) {
            return "assistant";
        }
        return switch (type.toUpperCase()) {
            case "USER" -> "user";
            case "ASSISTANT" -> "assistant";
            case "SYSTEM" -> "system";
            case "TOOL" -> "tool";
            default -> type.toLowerCase();
        };
    }
}
