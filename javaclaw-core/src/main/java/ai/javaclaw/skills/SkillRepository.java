package ai.javaclaw.skills;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface SkillRepository extends ListCrudRepository<Skill, String> {

    List<Skill> findAllByOwnerIdIsNull();

    /** Finds all global skills (no owner) that are enabled. */
    List<Skill> findAllByOwnerIdIsNullAndEnabledTrue();

    Optional<Skill> findByIdAndOwnerIdIsNull(String id);

    boolean existsByOwnerIdIsNullAndName(String name);
}
