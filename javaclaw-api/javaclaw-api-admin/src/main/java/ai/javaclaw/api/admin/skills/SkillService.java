package ai.javaclaw.api.admin.skills;

import ai.javaclaw.skills.Skill;
import ai.javaclaw.skills.SkillRepository;
import ai.javaclaw.skills.SkillVisibilityService;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SkillService {

    private final SkillRepository repository;
    private final SkillVisibilityService visibilityService;

    public SkillService(final SkillRepository repository, final SkillVisibilityService visibilityService) {
        this.repository = repository;
        this.visibilityService = visibilityService;
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

    public SkillDto create(final SkillDto draft) {
        final Skill skill = Skill.newGlobal(draft.name(), draft.description(), draft.enabled());
        return toDto(repository.save(skill));
    }

    public SkillDto update(final String id, final SkillDto patch) {
        final Skill current = repository
                .findByIdAndOwnerIdIsNull(id)
                .orElseThrow(() -> new NoSuchElementException("skill not found: " + id));
        final Skill updated = current.withPatch(patch.name(), patch.description(), patch.enabled());
        return toDto(repository.save(updated));
    }

    public void delete(final String id) {
        final Skill skill = repository
                .findByIdAndOwnerIdIsNull(id)
                .orElseThrow(() -> new NoSuchElementException("skill not found: " + id));
        repository.deleteById(skill.id());
    }

    /** Sets visibility (PUBLIC or RESTRICTED) for a skill. */
    public SkillDto setVisibility(final String skillId, final String visibility) {
        return toDto(visibilityService.setVisibility(skillId, visibility));
    }

    /** Gets allowed roles for a skill. */
    public Set<String> getAllowedRoles(final String skillId) {
        return visibilityService.getAllowedRoles(skillId);
    }

    /** Sets allowed roles for a RESTRICTED skill. */
    public void setAllowedRoles(final String skillId, final Set<String> roles) {
        visibilityService.setAllowedRoles(skillId, roles);
    }

    private SkillDto toDto(final Skill skill) {
        return new SkillDto(skill.id(), skill.name(), skill.description(), skill.enabled());
    }
}
