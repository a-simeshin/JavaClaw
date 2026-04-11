package ai.javaclaw.security.authz;

import ai.javaclaw.users.Permission;
import ai.javaclaw.users.PermissionService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PermissionResolvers {

    private final PermissionService permissionService;
    private final Map<String, PermissionCheck> resolvers = new HashMap<>();

    /** Register additional resolvers from other modules (e.g., ConversationSharingService). */
    public void register(String targetType, PermissionCheck check) {
        resolvers.put(targetType.toLowerCase(), check);
    }

    public Optional<PermissionCheck> get(String targetType) {
        return Optional.ofNullable(resolvers.get(targetType.toLowerCase()));
    }

    /**
     * Default check: delegates to PermissionService.userHasPermission().
     * Used for targetTypes without a registered resource-level resolver.
     */
    public boolean defaultCheck(String username, Object action) {
        try {
            Permission permission = Permission.valueOf(action.toString().toUpperCase());
            return permissionService.userHasPermission(username, permission);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
