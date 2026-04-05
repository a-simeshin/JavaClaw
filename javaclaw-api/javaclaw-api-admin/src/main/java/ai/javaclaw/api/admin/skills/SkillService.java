package ai.javaclaw.api.admin.skills;

import ai.javaclaw.skills.Skill;
import ai.javaclaw.skills.SkillRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class SkillService {

    private final SkillRepository repository;

    public SkillService(final SkillRepository repository) {
        this.repository = repository;
    }

    public List<SkillDto> list() {
        return repository.findAllByOwnerIdIsNull().stream().map(this::toDto).toList();
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

    private SkillDto toDto(final Skill skill) {
        return new SkillDto(skill.id(), skill.name(), skill.description(), skill.enabled());
    }
}
