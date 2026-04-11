package ai.javaclaw.users;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Service for checking granular permissions (Phase 13 — 15.2.2 + 15.2.3).
 *
 * <p>Role hierarchy: ADMIN > POWER_USER > USER.
 * Permissions are loaded from the {@code role_permissions} table and cached per role.
 */
@Service
public class PermissionService {

    /** Role hierarchy — higher roles inherit all permissions of lower roles. */
    private static final Map<String, Integer> ROLE_HIERARCHY = Map.of(
            "USER", 0,
            "POWER_USER", 1,
            "ADMIN", 2);

    private final RolePermissionRepository repository;
    private final AppUserRepository userRepository;
    private final Map<String, Set<Permission>> cache = new ConcurrentHashMap<>();

    public PermissionService(RolePermissionRepository repository, AppUserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    /**
     * Get all permissions for a role (from DB, cached).
     */
    public Set<Permission> getPermissionsForRole(String role) {
        Assert.hasText(role, "role must not be blank");
        return cache.computeIfAbsent(role, this::loadPermissions);
    }

    /**
     * Check if a role has a specific permission.
     */
    public boolean roleHasPermission(String role, Permission permission) {
        Assert.hasText(role, "role must not be blank");
        Assert.notNull(permission, "permission must not be null");
        return getPermissionsForRole(role).contains(permission);
    }

    /**
     * Check if a user (by username) has a specific permission.
     */
    public boolean userHasPermission(String username, Permission permission) {
        Assert.hasText(username, "username must not be blank");
        Assert.notNull(permission, "permission must not be null");
        Optional<AppUser> user = userRepository.findByUsername(username);
        return user.map(u -> roleHasPermission(u.role(), permission)).orElse(false);
    }

    /**
     * Get all permissions for a user (by username).
     */
    public Set<Permission> getUserPermissions(String username) {
        Assert.hasText(username, "username must not be blank");
        Optional<AppUser> user = userRepository.findByUsername(username);
        return user.map(u -> getPermissionsForRole(u.role())).orElse(Collections.emptySet());
    }

    /**
     * Compare two roles in the hierarchy. Returns positive if role1 > role2.
     */
    public int compareRoles(String role1, String role2) {
        int rank1 = ROLE_HIERARCHY.getOrDefault(role1, -1);
        int rank2 = ROLE_HIERARCHY.getOrDefault(role2, -1);
        return Integer.compare(rank1, rank2);
    }

    /**
     * Check if role1 is higher or equal to role2 in the hierarchy.
     */
    public boolean isRoleAtLeast(String role, String minimumRole) {
        return compareRoles(role, minimumRole) >= 0;
    }

    /**
     * Get all known roles ordered by hierarchy (ascending).
     */
    public List<String> getRoleHierarchy() {
        return List.of("USER", "POWER_USER", "ADMIN");
    }

    /**
     * Grant a permission to a role. Evicts cache for that role.
     */
    public void grantPermission(String role, Permission permission) {
        Assert.hasText(role, "role must not be blank");
        Assert.notNull(permission, "permission must not be null");
        if (!repository.existsByRoleAndPermission(role, permission.name())) {
            repository.save(RolePermission.create(role, permission.name()));
        }
        cache.remove(role);
    }

    /**
     * Revoke a permission from a role. Evicts cache for that role.
     */
    public void revokePermission(String role, Permission permission) {
        Assert.hasText(role, "role must not be blank");
        Assert.notNull(permission, "permission must not be null");
        repository.deleteByRoleAndPermission(role, permission.name());
        cache.remove(role);
    }

    /**
     * Clear the permissions cache (e.g. after bulk changes).
     */
    public void clearCache() {
        cache.clear();
    }

    private Set<Permission> loadPermissions(String role) {
        List<String> permissionNames = repository.findPermissionsByRole(role);
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (String name : permissionNames) {
            try {
                permissions.add(Permission.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // Skip unknown permissions (e.g. from future migrations)
            }
        }
        return Collections.unmodifiableSet(permissions);
    }
}
