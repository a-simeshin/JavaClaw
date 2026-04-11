package ai.javaclaw.api.admin.users;

import ai.javaclaw.users.CustomRole;
import ai.javaclaw.users.CustomRoleService;
import ai.javaclaw.users.Permission;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin CRUD endpoints for custom role management (Phase 13 — 15.2.4). */
@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final CustomRoleService roleService;

    public RoleController(CustomRoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public List<RoleDto> list() {
        return roleService.listAll().stream().map(this::toDto).toList();
    }

    @GetMapping("/{name}")
    public ResponseEntity<RoleDto> get(@PathVariable String name) {
        return roleService
                .findByName(name)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<RoleDto> create(@RequestBody CreateRoleRequest request) {
        Set<Permission> permissions = parsePermissions(request.permissions());
        CustomRole role = roleService.create(request.name(), request.description(), permissions);
        return ResponseEntity.status(201).body(toDto(role));
    }

    @PutMapping("/{name}/description")
    public ResponseEntity<RoleDto> updateDescription(
            @PathVariable String name, @RequestBody UpdateDescriptionRequest request) {
        CustomRole role = roleService.updateDescription(name, request.description());
        return ResponseEntity.ok(toDto(role));
    }

    @PutMapping("/{name}/permissions")
    public ResponseEntity<Void> setPermissions(@PathVariable String name, @RequestBody SetPermissionsRequest request) {
        Set<Permission> permissions = parsePermissions(request.permissions());
        roleService.setPermissions(name, permissions);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        roleService.delete(name);
        return ResponseEntity.noContent().build();
    }

    private RoleDto toDto(CustomRole role) {
        Set<Permission> permissions = roleService.getPermissions(role.name());
        List<String> permNames =
                permissions.stream().map(Permission::name).sorted().toList();
        return new RoleDto(role.name(), role.description(), role.builtIn(), permNames);
    }

    private Set<Permission> parsePermissions(List<String> names) {
        if (names == null || names.isEmpty()) {
            return Set.of();
        }
        return names.stream().map(Permission::valueOf).collect(Collectors.toSet());
    }

    public record RoleDto(String name, String description, boolean builtIn, List<String> permissions) {}

    public record CreateRoleRequest(String name, String description, List<String> permissions) {}

    public record UpdateDescriptionRequest(String description) {}

    public record SetPermissionsRequest(List<String> permissions) {}

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<java.util.Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(java.util.Map.of("error", ex.getMessage()));
    }
}
