package ai.javaclaw.api.admin.users;

import ai.javaclaw.agent.config.RoleAgentConfig;
import ai.javaclaw.agent.config.RoleAgentConfigService;
import ai.javaclaw.agent.config.RoleModelAllowlistService;
import ai.javaclaw.users.CustomRole;
import ai.javaclaw.users.CustomRoleService;
import ai.javaclaw.users.Permission;
import jakarta.servlet.http.HttpServletRequest;
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
    private final RoleAgentConfigService agentConfigService;
    private final RoleModelAllowlistService modelAllowlistService;

    public RoleController(
            CustomRoleService roleService,
            RoleAgentConfigService agentConfigService,
            RoleModelAllowlistService modelAllowlistService) {
        this.roleService = roleService;
        this.agentConfigService = agentConfigService;
        this.modelAllowlistService = modelAllowlistService;
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

    // --- Agent config endpoints (15.6.1) ---

    @GetMapping("/{name}/agent-config")
    public ResponseEntity<AgentConfigDto> getAgentConfig(@PathVariable String name) {
        return agentConfigService
                .getForRole(name)
                .map(this::toAgentConfigDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/agent-configs")
    public List<AgentConfigDto> listAgentConfigs() {
        return agentConfigService.listAll().stream().map(this::toAgentConfigDto).toList();
    }

    @PutMapping("/{name}/agent-config")
    public ResponseEntity<AgentConfigDto> saveAgentConfig(
            @PathVariable String name, @RequestBody SaveAgentConfigRequest request) {
        RoleAgentConfig saved = agentConfigService.save(
                name,
                request.modelId(),
                request.thinkingEnabled() != null ? request.thinkingEnabled() : false,
                request.thinkingBudget() != null ? request.thinkingBudget() : 10000,
                request.fallbackModels(),
                request.maxContextTokens() != null ? request.maxContextTokens() : 200000);
        return ResponseEntity.ok(toAgentConfigDto(saved));
    }

    @DeleteMapping("/{name}/agent-config")
    public ResponseEntity<Void> deleteAgentConfig(@PathVariable String name) {
        agentConfigService.delete(name);
        return ResponseEntity.noContent().build();
    }

    private AgentConfigDto toAgentConfigDto(RoleAgentConfig config) {
        return new AgentConfigDto(
                config.role(),
                config.modelId(),
                config.thinkingEnabled(),
                config.thinkingBudget(),
                config.fallbackModels(),
                config.fallbackModelList(),
                config.maxContextTokens());
    }

    public record AgentConfigDto(
            String role,
            String modelId,
            boolean thinkingEnabled,
            int thinkingBudget,
            String fallbackModels,
            List<String> fallbackModelList,
            int maxContextTokens) {}

    public record SaveAgentConfigRequest(
            String modelId,
            Boolean thinkingEnabled,
            Integer thinkingBudget,
            String fallbackModels,
            Integer maxContextTokens) {}

    // --- Model allowlist endpoints (15.6.4) ---

    @GetMapping("/{name}/allowed-models")
    public List<String> getAllowedModels(@PathVariable String name) {
        return modelAllowlistService.getAllowedModels(name);
    }

    @PutMapping("/{name}/allowed-models")
    public ResponseEntity<Void> setAllowedModels(
            @PathVariable String name, @RequestBody SetAllowedModelsRequest request) {
        modelAllowlistService.setAllowedModels(name, request.modelIds());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{name}/allowed-models")
    public ResponseEntity<Void> addAllowedModel(@PathVariable String name, @RequestBody AddModelRequest request) {
        modelAllowlistService.addAllowedModel(name, request.modelId());
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/{name}/allowed-models/**")
    public ResponseEntity<Void> removeAllowedModel(@PathVariable String name, HttpServletRequest request) {
        final String modelId = extractModelId(request, name);
        modelAllowlistService.removeAllowedModel(name, modelId);
        return ResponseEntity.noContent().build();
    }

    private static String extractModelId(HttpServletRequest request, String roleName) {
        String path = request.getRequestURI();
        String prefix = "/api/roles/" + roleName + "/allowed-models/";
        int idx = path.indexOf(prefix);
        return idx >= 0 ? path.substring(idx + prefix.length()) : "";
    }

    public record SetAllowedModelsRequest(List<String> modelIds) {}

    public record AddModelRequest(String modelId) {}

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<java.util.Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(java.util.Map.of("error", ex.getMessage()));
    }
}
