package ai.javaclaw.security.authz;

import ai.javaclaw.users.Permission;
import ai.javaclaw.users.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Registers default PermissionResolvers for conversation, task, skill, mcp types.
 *
 * <p>Each resolver implements admin-bypass: a user with {@code CONVERSATION_ACCESS_ALL}
 * (or equivalent listing permission) can access any resource, otherwise ownership/sharing
 * rules apply via delegation to {@code ConversationSharingService}.
 *
 * <p>Delegation to {@code ConversationSharingService} is done via reflection to avoid a
 * compile-time dependency on the conversations domain from the security module.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultPermissionResolversRegistrar implements InitializingBean {

    private final PermissionResolvers resolvers;
    private final PermissionService permissionService;
    private final ApplicationContext applicationContext;

    @Override
    public void afterPropertiesSet() {
        // Conversation: CONVERSATION_ACCESS_ALL bypasses ownership/sharing check;
        // otherwise we delegate to ConversationSharingService.hasAccess(conversationId, userId).
        resolvers.register("conversation", (username, targetId, action) -> {
            if (hasPermission(username, Permission.CONVERSATION_ACCESS_ALL)) {
                return true;
            }
            try {
                Object sharingBean = applicationContext.getBean("conversationSharingService");
                Object userResolverBean = applicationContext.getBean("userResolver");
                String userId = (String) userResolverBean
                        .getClass()
                        .getMethod("resolveUserId", String.class)
                        .invoke(userResolverBean, username);
                Object result = sharingBean
                        .getClass()
                        .getMethod("hasAccess", String.class, String.class, String.class)
                        .invoke(sharingBean, targetId == null ? null : targetId.toString(), userId, username);
                return Boolean.TRUE.equals(result);
            } catch (Exception e) {
                log.warn(
                        "Could not delegate to conversationSharingService, falling back to CONVERSATION_LIST: {}",
                        e.getMessage());
                return hasPermission(username, Permission.CONVERSATION_LIST);
            }
        });

        // Task: delegate to PermissionService (no resource-level ownership in Permission enum currently).
        resolvers.register("task", (username, targetId, action) -> hasPermission(username, Permission.TASK_LIST));

        // Skill: SKILL_LIST for read, SKILL_CREATE/UPDATE/DELETE/EXECUTE for modifications.
        resolvers.register("skill", (username, targetId, action) -> {
            String perm = "SKILL_" + action.toString().toUpperCase();
            try {
                return hasPermission(username, Permission.valueOf(perm));
            } catch (IllegalArgumentException e) {
                return hasPermission(username, Permission.SKILL_LIST);
            }
        });

        // Mcp: MCP_LIST / MCP_CREATE / MCP_UPDATE / MCP_DELETE / MCP_CONNECT.
        resolvers.register("mcp", (username, targetId, action) -> {
            String perm = "MCP_" + action.toString().toUpperCase();
            try {
                return hasPermission(username, Permission.valueOf(perm));
            } catch (IllegalArgumentException e) {
                return hasPermission(username, Permission.MCP_LIST);
            }
        });

        log.info("Registered default permission resolvers: conversation, task, skill, mcp");
    }

    private boolean hasPermission(String username, Permission permission) {
        try {
            return permissionService.userHasPermission(username, permission);
        } catch (Exception e) {
            log.debug("Permission check failed for {} / {}: {}", username, permission, e.getMessage());
            return false;
        }
    }
}
