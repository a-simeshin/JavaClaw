package ai.javaclaw.skills;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Immutable audit record for skill management operations.
 * Tracks create, update, delete, enable/disable, visibility and allowlist changes (15.3.4).
 */
@Table("skill_usage_audit")
public record SkillUsageAudit(
        @Id Long id,
        @Column("skill_id") String skillId,
        @Column("skill_name") String skillName,
        @Column("event_type") String eventType,
        @Column("username") String username,
        @Column("detail") String detail,
        @Column("created_at") Instant createdAt) {

    public static final String EVENT_CREATED = "created";
    public static final String EVENT_UPDATED = "updated";
    public static final String EVENT_DELETED = "deleted";
    public static final String EVENT_ENABLED = "enabled";
    public static final String EVENT_DISABLED = "disabled";
    public static final String EVENT_VISIBILITY_CHANGED = "visibility_changed";
    public static final String EVENT_ALLOWLIST_CHANGED = "allowlist_changed";

    public static SkillUsageAudit of(
            String skillId, String skillName, String eventType, String username, String detail) {
        return new SkillUsageAudit(null, skillId, skillName, eventType, username, detail, Instant.now());
    }

    public static SkillUsageAudit created(String skillId, String skillName, String username) {
        return of(skillId, skillName, EVENT_CREATED, username, null);
    }

    public static SkillUsageAudit updated(String skillId, String skillName, String username, String detail) {
        return of(skillId, skillName, EVENT_UPDATED, username, detail);
    }

    public static SkillUsageAudit deleted(String skillId, String skillName, String username) {
        return of(skillId, skillName, EVENT_DELETED, username, null);
    }

    public static SkillUsageAudit enabled(String skillId, String skillName, String username) {
        return of(skillId, skillName, EVENT_ENABLED, username, null);
    }

    public static SkillUsageAudit disabled(String skillId, String skillName, String username) {
        return of(skillId, skillName, EVENT_DISABLED, username, null);
    }

    public static SkillUsageAudit visibilityChanged(
            String skillId, String skillName, String username, String oldVis, String newVis) {
        return of(skillId, skillName, EVENT_VISIBILITY_CHANGED, username, oldVis + " -> " + newVis);
    }

    public static SkillUsageAudit allowlistChanged(String skillId, String skillName, String username, String detail) {
        return of(skillId, skillName, EVENT_ALLOWLIST_CHANGED, username, detail);
    }
}
