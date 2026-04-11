package ai.javaclaw.skills;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Maps a skill to a role that is allowed to see/execute it when visibility is RESTRICTED. */
@Table("skill_role_allowlist")
public record SkillRoleAllowlist(
        @Id String id,
        @Column("skill_id") String skillId,
        @Column("role") String role,
        @Column("created_at") Instant createdAt) {

    public static SkillRoleAllowlist create(final String skillId, final String role) {
        return new SkillRoleAllowlist(null, skillId, role, Instant.now());
    }
}
