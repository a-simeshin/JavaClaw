package ai.javaclaw.users;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Service for custom role management — CRUD + permission assignment (Phase 13 — 15.2.4).
 */
@Service
public class CustomRoleService {

    private static final Set<String> BUILT_IN_ROLES = Set.of("ADMIN", "POWER_USER", "USER");

    private final CustomRoleRepository roleRepository;
    private final RolePermissionRepository permissionRepository;
    private final PermissionService permissionService;

    public CustomRoleService(
            CustomRoleRepository roleRepository,
            RolePermissionRepository permissionRepository,
            PermissionService permissionService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.permissionService = permissionService;
    }

    /** List all roles (built-in first, then custom alphabetically). */
    public List<CustomRole> listAll() {
        return roleRepository.findAllOrdered();
    }

    /** List only custom (non-built-in) roles. */
    public List<CustomRole> listCustom() {
        return roleRepository.findAllCustom();
    }

    /** Find role by name. */
    public Optional<CustomRole> findByName(String name) {
        return roleRepository.findByName(name);
    }

    /** Check if a role name exists (built-in or custom). */
    public boolean roleExists(String name) {
        return roleRepository.existsByName(name);
    }

    /** Check if a role name is a built-in role. */
    public boolean isBuiltIn(String name) {
        return BUILT_IN_ROLES.contains(name);
    }

    /**
     * Create a new custom role with a description and initial set of permissions.
     *
     * @throws IllegalArgumentException if name is blank, already exists, or is a reserved name
     */
    public CustomRole create(String name, String description, Set<Permission> permissions) {
        Assert.hasText(name, "role name must not be blank");
        String normalized = name.toUpperCase().replaceAll("[^A-Z0-9_]", "_");
        if (BUILT_IN_ROLES.contains(normalized)) {
            throw new IllegalArgumentException("Cannot create role with reserved name: " + normalized);
        }
        if (roleRepository.existsByName(normalized)) {
            throw new IllegalArgumentException("Role already exists: " + normalized);
        }
        CustomRole role = roleRepository.save(CustomRole.create(normalized, description));
        if (permissions != null) {
            for (Permission p : permissions) {
                permissionService.grantPermission(normalized, p);
            }
        }
        return role;
    }

    /**
     * Update a custom role's description.
     *
     * @throws IllegalArgumentException if role not found or is built-in
     */
    public CustomRole updateDescription(String name, String description) {
        Assert.hasText(name, "role name must not be blank");
        CustomRole existing = roleRepository
                .findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + name));
        if (existing.builtIn()) {
            throw new IllegalArgumentException("Cannot modify built-in role: " + name);
        }
        return roleRepository.save(
                new CustomRole(existing.id(), existing.name(), description, existing.builtIn(), existing.createdAt()));
    }

    /**
     * Set the full permission set for a custom role (replaces all existing permissions).
     *
     * @throws IllegalArgumentException if role not found or is built-in
     */
    public void setPermissions(String name, Set<Permission> permissions) {
        Assert.hasText(name, "role name must not be blank");
        Assert.notNull(permissions, "permissions must not be null");
        if (!roleRepository.existsByName(name)) {
            throw new IllegalArgumentException("Role not found: " + name);
        }
        if (isBuiltIn(name)) {
            throw new IllegalArgumentException("Cannot bulk-set permissions on built-in role: " + name);
        }
        permissionRepository.deleteAllByRole(name);
        for (Permission p : permissions) {
            permissionRepository.save(RolePermission.create(name, p.name()));
        }
        permissionService.clearCache();
    }

    /**
     * Delete a custom role and all its permission mappings.
     *
     * @throws IllegalArgumentException if role not found, is built-in, or has assigned users
     */
    public void delete(String name) {
        Assert.hasText(name, "role name must not be blank");
        CustomRole existing = roleRepository
                .findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + name));
        if (existing.builtIn()) {
            throw new IllegalArgumentException("Cannot delete built-in role: " + name);
        }
        permissionRepository.deleteAllByRole(name);
        roleRepository.delete(existing);
        permissionService.clearCache();
    }

    /** Get permissions for a role (delegates to PermissionService). */
    public Set<Permission> getPermissions(String name) {
        return permissionService.getPermissionsForRole(name);
    }
}
