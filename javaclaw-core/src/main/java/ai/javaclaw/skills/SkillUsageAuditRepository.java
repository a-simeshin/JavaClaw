package ai.javaclaw.skills;

import java.time.Instant;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface SkillUsageAuditRepository extends ListCrudRepository<SkillUsageAudit, Long> {

    List<SkillUsageAudit> findBySkillIdOrderByCreatedAtDesc(String skillId);

    List<SkillUsageAudit> findByUsernameOrderByCreatedAtDesc(String username);

    List<SkillUsageAudit> findByEventTypeOrderByCreatedAtDesc(String eventType);

    List<SkillUsageAudit> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);

    List<SkillUsageAudit> findAllByOrderByCreatedAtDesc();
}
