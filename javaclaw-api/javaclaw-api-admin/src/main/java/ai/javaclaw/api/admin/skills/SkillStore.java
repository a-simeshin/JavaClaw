package ai.javaclaw.api.admin.skills;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * In-memory skill registry. Intended as a drop-in placeholder; a future task
 * will wire this to a persistent store (workspace/skills/*.md or JDBC).
 */
@Component
public class SkillStore {

    private final ConcurrentMap<String, SkillDto> items = new ConcurrentHashMap<>();

    public List<SkillDto> list() {
        return List.copyOf(items.values());
    }

    public SkillDto create(SkillDto draft) {
        String id = UUID.randomUUID().toString();
        SkillDto created = new SkillDto(id, draft.name(), draft.description(), draft.enabled());
        items.put(id, created);
        return created;
    }

    public SkillDto update(String id, SkillDto patch) {
        SkillDto current = items.get(id);
        if (current == null) {
            throw new NoSuchElementException("skill not found: " + id);
        }
        SkillDto updated = new SkillDto(
                id,
                patch.name() != null ? patch.name() : current.name(),
                patch.description() != null ? patch.description() : current.description(),
                patch.enabled());
        items.put(id, updated);
        return updated;
    }

    public void delete(String id) {
        if (items.remove(id) == null) {
            throw new NoSuchElementException("skill not found: " + id);
        }
    }
}
