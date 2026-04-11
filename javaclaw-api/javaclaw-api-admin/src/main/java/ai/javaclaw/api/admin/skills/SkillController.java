package ai.javaclaw.api.admin.skills;

import ai.javaclaw.skills.SkillUsageAuditService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for agent skills with visibility/allowlist management. */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService service;
    private final SkillUsageAuditService auditService;

    public SkillController(final SkillService service, final SkillUsageAuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    public List<SkillDto> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<SkillDto> create(@Valid @RequestBody final SkillDto body, final Principal principal) {
        return ResponseEntity.status(201).body(service.create(body, principalName(principal)));
    }

    @PutMapping("/{id}")
    public SkillDto update(
            @PathVariable final String id, @Valid @RequestBody final SkillDto body, final Principal principal) {
        return service.update(id, body, principalName(principal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable final String id, final Principal principal) {
        service.delete(id, principalName(principal));
        return ResponseEntity.noContent().build();
    }

    /** Set visibility for a skill: {"visibility": "PUBLIC"} or {"visibility": "RESTRICTED"}. */
    @PutMapping("/{id}/visibility")
    public SkillDto setVisibility(
            @PathVariable final String id, @RequestBody final Map<String, String> body, final Principal principal) {
        final String visibility = body.getOrDefault("visibility", "PUBLIC");
        return service.setVisibility(id, visibility, principalName(principal));
    }

    /** Get allowed roles for a RESTRICTED skill. */
    @GetMapping("/{id}/allowlist")
    public Set<String> getAllowlist(@PathVariable final String id) {
        return service.getAllowedRoles(id);
    }

    /** Set allowed roles for a RESTRICTED skill: {"roles": ["USER", "POWER_USER"]}. */
    @PutMapping("/{id}/allowlist")
    public ResponseEntity<Void> setAllowlist(
            @PathVariable final String id,
            @RequestBody final Map<String, Set<String>> body,
            final Principal principal) {
        final Set<String> roles = body.getOrDefault("roles", Set.of());
        service.setAllowedRoles(id, roles, principalName(principal));
        return ResponseEntity.noContent().build();
    }

    /** Query skill audit log. Admin-only. */
    @GetMapping("/audit")
    public List<ai.javaclaw.skills.SkillUsageAudit> auditLog(
            @RequestParam(required = false) final String skillId,
            @RequestParam(required = false) final String username,
            @RequestParam(required = false) final String eventType) {
        if (skillId != null) return auditService.findBySkillId(skillId);
        if (username != null) return auditService.findByUsername(username);
        if (eventType != null) return auditService.findByEventType(eventType);
        return auditService.findAll();
    }

    private static String principalName(final Principal principal) {
        return principal != null ? principal.getName() : "system";
    }
}
