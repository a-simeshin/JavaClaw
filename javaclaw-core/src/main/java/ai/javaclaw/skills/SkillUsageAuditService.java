package ai.javaclaw.skills;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for recording and querying skill usage audit events (15.3.4).
 * Write operations are async to avoid blocking the skill management flow.
 */
@Service
public class SkillUsageAuditService {

    private static final Logger log = LoggerFactory.getLogger(SkillUsageAuditService.class);

    private final SkillUsageAuditRepository repository;

    public SkillUsageAuditService(SkillUsageAuditRepository repository) {
        this.repository = repository;
    }

    @Async
    public void logCreated(String skillId, String skillName, String username) {
        save(SkillUsageAudit.created(skillId, skillName, username));
    }

    @Async
    public void logUpdated(String skillId, String skillName, String username, String detail) {
        save(SkillUsageAudit.updated(skillId, skillName, username, detail));
    }

    @Async
    public void logDeleted(String skillId, String skillName, String username) {
        save(SkillUsageAudit.deleted(skillId, skillName, username));
    }

    @Async
    public void logEnabled(String skillId, String skillName, String username) {
        save(SkillUsageAudit.enabled(skillId, skillName, username));
    }

    @Async
    public void logDisabled(String skillId, String skillName, String username) {
        save(SkillUsageAudit.disabled(skillId, skillName, username));
    }

    @Async
    public void logVisibilityChanged(String skillId, String skillName, String username, String oldVis, String newVis) {
        save(SkillUsageAudit.visibilityChanged(skillId, skillName, username, oldVis, newVis));
    }

    @Async
    public void logAllowlistChanged(String skillId, String skillName, String username, String detail) {
        save(SkillUsageAudit.allowlistChanged(skillId, skillName, username, detail));
    }

    public List<SkillUsageAudit> findBySkillId(String skillId) {
        return repository.findBySkillIdOrderByCreatedAtDesc(skillId);
    }

    public List<SkillUsageAudit> findByUsername(String username) {
        return repository.findByUsernameOrderByCreatedAtDesc(username);
    }

    public List<SkillUsageAudit> findByEventType(String eventType) {
        return repository.findByEventTypeOrderByCreatedAtDesc(eventType);
    }

    public List<SkillUsageAudit> findByDateRange(Instant from, Instant to) {
        return repository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
    }

    public List<SkillUsageAudit> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    private void save(SkillUsageAudit audit) {
        try {
            repository.save(audit);
        } catch (Exception e) {
            log.warn("Failed to save skill usage audit ({}): {}", audit.eventType(), e.getMessage());
        }
    }
}
