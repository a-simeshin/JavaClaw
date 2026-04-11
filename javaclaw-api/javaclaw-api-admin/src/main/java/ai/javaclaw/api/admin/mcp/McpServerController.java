package ai.javaclaw.api.admin.mcp;

import ai.javaclaw.users.UserResolver;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD + status + visibility/allowlist + personal server endpoints for MCP servers. */
@RestController
@RequestMapping("/api/mcp-servers")
public class McpServerController {

    private final McpServerService service;
    private final UserResolver userResolver;

    public McpServerController(final McpServerService service, final UserResolver userResolver) {
        this.service = service;
        this.userResolver = userResolver;
    }

    @GetMapping
    public List<McpServerDto> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<McpServerDto> create(@Valid @RequestBody final McpServerDto body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PutMapping("/{id}")
    public McpServerDto update(@PathVariable final String id, @Valid @RequestBody final McpServerDto body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable final String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/status")
    public McpServerStatusDto status(@PathVariable final String id) {
        return service.status(id);
    }

    @GetMapping("/tools")
    public ToolCacheInfoDto tools() {
        return service.toolCacheInfo();
    }

    /** Set visibility for a global MCP server: {"visibility": "PUBLIC"} or {"visibility": "RESTRICTED"}. */
    @PutMapping("/{id}/visibility")
    public McpServerDto setVisibility(@PathVariable final String id, @RequestBody final Map<String, String> body) {
        final String visibility = body.getOrDefault("visibility", "PUBLIC");
        return service.setVisibility(id, visibility);
    }

    /** Get allowed roles for a RESTRICTED MCP server. */
    @GetMapping("/{id}/allowlist")
    public Set<String> getAllowlist(@PathVariable final String id) {
        return service.getAllowedRoles(id);
    }

    /** Set allowed roles for a RESTRICTED MCP server: {"roles": ["USER", "POWER_USER"]}. */
    @PutMapping("/{id}/allowlist")
    public ResponseEntity<Void> setAllowlist(
            @PathVariable final String id, @RequestBody final Map<String, Set<String>> body) {
        final Set<String> roles = body.getOrDefault("roles", Set.of());
        service.setAllowedRoles(id, roles);
        return ResponseEntity.noContent().build();
    }

    /** List personal MCP servers for the authenticated user. */
    @GetMapping("/personal")
    public List<McpServerDto> listPersonal(final Principal principal) {
        final String userId = userResolver.resolveUserId(principal.getName());
        return service.listPersonal(userId);
    }

    /** Create a personal MCP server for the authenticated user. */
    @PostMapping("/personal")
    public ResponseEntity<McpServerDto> createPersonal(
            final Principal principal, @Valid @RequestBody final McpServerDto body) {
        final String userId = userResolver.resolveUserId(principal.getName());
        return ResponseEntity.status(201).body(service.createPersonal(userId, body));
    }

    /** Delete a personal MCP server (must be owned by the authenticated user). */
    @DeleteMapping("/personal/{id}")
    public ResponseEntity<Void> deletePersonal(@PathVariable final String id, final Principal principal) {
        final String userId = userResolver.resolveUserId(principal.getName());
        service.deletePersonal(id, userId);
        return ResponseEntity.noContent().build();
    }
}
