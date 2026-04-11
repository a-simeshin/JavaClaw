package ai.javaclaw.security.authz;

import ai.javaclaw.users.Permission;
import ai.javaclaw.users.PermissionService;
import java.io.Serializable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JavaClawPermissionEvaluator implements PermissionEvaluator {

    private final PermissionResolvers permissionResolvers;
    private final PermissionService permissionService;

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        String username = authentication.getName();
        // targetDomainObject is the actual object — delegate to PermissionService
        return defaultPermissionCheck(username, permission);
    }

    @Override
    public boolean hasPermission(
            Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        String username = authentication.getName();

        // Try registered resource-level resolver first
        return permissionResolvers
                .get(targetType)
                .map(resolver -> resolver.check(username, targetId, permission))
                .orElseGet(() -> defaultPermissionCheck(username, permission));
    }

    private boolean defaultPermissionCheck(String username, Object permission) {
        try {
            Permission perm = Permission.valueOf(permission.toString().toUpperCase());
            return permissionService.userHasPermission(username, perm);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown permission: {}", permission);
            return false;
        }
    }
}
