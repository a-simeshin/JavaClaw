package ai.javaclaw.api.admin.skills;

import jakarta.validation.Valid;
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

/** CRUD endpoints for agent skills with visibility/allowlist management. */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService service;

    public SkillController(final SkillService service) {
        this.service = service;
    }

    @GetMapping
    public List<SkillDto> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<SkillDto> create(@Valid @RequestBody final SkillDto body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PutMapping("/{id}")
    public SkillDto update(@PathVariable final String id, @Valid @RequestBody final SkillDto body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable final String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Set visibility for a skill: {"visibility": "PUBLIC"} or {"visibility": "RESTRICTED"}. */
    @PutMapping("/{id}/visibility")
    public SkillDto setVisibility(@PathVariable final String id, @RequestBody final Map<String, String> body) {
        final String visibility = body.getOrDefault("visibility", "PUBLIC");
        return service.setVisibility(id, visibility);
    }

    /** Get allowed roles for a RESTRICTED skill. */
    @GetMapping("/{id}/allowlist")
    public Set<String> getAllowlist(@PathVariable final String id) {
        return service.getAllowedRoles(id);
    }

    /** Set allowed roles for a RESTRICTED skill: {"roles": ["USER", "POWER_USER"]}. */
    @PutMapping("/{id}/allowlist")
    public ResponseEntity<Void> setAllowlist(
            @PathVariable final String id, @RequestBody final Map<String, Set<String>> body) {
        final Set<String> roles = body.getOrDefault("roles", Set.of());
        service.setAllowedRoles(id, roles);
        return ResponseEntity.noContent().build();
    }
}
