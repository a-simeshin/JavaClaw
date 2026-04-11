package ai.javaclaw.api.admin.skills;

import ai.javaclaw.skills.Skill;
import ai.javaclaw.skills.SkillRepository;
import ai.javaclaw.skills.SkillUsageAuditService;
import ai.javaclaw.skills.SkillVisibilityService;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SkillService {

    private final SkillRepository repository;
    private final SkillVisibilityService visibilityService;
    private final SkillUsageAuditService auditService;

    public SkillService(
            final SkillRepository repository,
            final SkillVisibilityService visibilityService,
            final SkillUsageAuditService auditService) {
        this.repository = repository;
        this.visibilityService = visibilityService;
        this.auditService = auditService;
    }

    public List<SkillDto> list() {
        return repository.findAllByOwnerIdIsNull().stream().map(this::toDto).toList();
    }

    /** Lists skills visible to the given role (PUBLIC + allowlisted RESTRICTED). */
    public List<SkillDto> listForRole(final String role) {
        return visibilityService.listVisibleSkills(role).stream()
                .map(this::toDto)
                .toList();
    }

    public SkillDto create(final SkillDto draft, final String username) {
        final Skill skill = Skill.newGlobal(draft.name(), draft.description(), draft.enabled());
        final Skill saved = repository.save(skill);
        auditService.logCreated(saved.id(), saved.name(), username);
        return toDto(saved);
    }

    public SkillDto update(final String id, final SkillDto patch, final String username) {
        final Skill current = repository
                .findByIdAndOwnerIdIsNull(id)
                .orElseThrow(() -> new NoSuchElementException("skill not found: " + id));
        final Skill updated = current.withPatch(patch.name(), patch.description(), patch.enabled());
        final Skill saved = repository.save(updated);
        final boolean enabledChanged = current.enabled() != saved.enabled();
        if (enabledChanged) {
            if (saved.enabled()) {
                auditService.logEnabled(saved.id(), saved.name(), username);
            } else {
                auditService.logDisabled(saved.id(), saved.name(), username);
            }
        }
        auditService.logUpdated(saved.id(), saved.name(), username, null);
        return toDto(saved);
    }

    public void delete(final String id, final String username) {
        final Skill skill = repository
                .findByIdAndOwnerIdIsNull(id)
                .orElseThrow(() -> new NoSuchElementException("skill not found: " + id));
        repository.deleteById(skill.id());
        auditService.logDeleted(skill.id(), skill.name(), username);
    }

    /** Sets visibility (PUBLIC or RESTRICTED) for a skill. */
    public SkillDto setVisibility(final String skillId, final String visibility, final String username) {
        final Skill before = repository
                .findById(skillId)
                .orElseThrow(() -> new NoSuchElementException("skill not found: " + skillId));
        final String oldVis = before.visibility();
        final Skill result = visibilityService.setVisibility(skillId, visibility);
        auditService.logVisibilityChanged(result.id(), result.name(), username, oldVis, visibility);
        return toDto(result);
    }

    /** Gets allowed roles for a skill. */
    public Set<String> getAllowedRoles(final String skillId) {
        return visibilityService.getAllowedRoles(skillId);
    }

    /** Sets allowed roles for a RESTRICTED skill. */
    public void setAllowedRoles(final String skillId, final Set<String> roles, final String username) {
        final Skill skill = repository
                .findById(skillId)
                .orElseThrow(() -> new NoSuchElementException("skill not found: " + skillId));
        visibilityService.setAllowedRoles(skillId, roles);
        auditService.logAllowlistChanged(skill.id(), skill.name(), username, "roles=" + roles);
    }

    private SkillDto toDto(final Skill skill) {
        return new SkillDto(skill.id(), skill.name(), skill.description(), skill.enabled());
    }
}
