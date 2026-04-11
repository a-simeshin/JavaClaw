package ai.javaclaw.skills;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("skills")
public record Skill(
        @Id String id,
        @Column("owner_id") String ownerId,
        @Column("name") String name,
        @Column("description") String description,
        @Column("content") String content,
        @Column("enabled") boolean enabled,
        @Column("visibility") String visibility,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt) {

    /** Visibility: all roles can see the skill. */
    public static final String VISIBILITY_PUBLIC = "PUBLIC";
    /** Visibility: only allowlisted roles can see the skill. */
    public static final String VISIBILITY_RESTRICTED = "RESTRICTED";

    public static Skill newGlobal(final String name, final String description, final boolean enabled) {
        return new Skill(null, null, name, description, null, enabled, VISIBILITY_PUBLIC, Instant.now(), Instant.now());
    }

    public Skill withPatch(final String patchName, final String patchDescription, final boolean patchEnabled) {
        return new Skill(
                this.id,
                this.ownerId,
                patchName != null ? patchName : this.name,
                patchDescription != null ? patchDescription : this.description,
                this.content,
                patchEnabled,
                this.visibility,
                this.createdAt,
                Instant.now());
    }

    public Skill withVisibility(final String newVisibility) {
        return new Skill(
                this.id,
                this.ownerId,
                this.name,
                this.description,
                this.content,
                this.enabled,
                newVisibility,
                this.createdAt,
                Instant.now());
    }

    public boolean isPublic() {
        return VISIBILITY_PUBLIC.equals(visibility);
    }

    public boolean isRestricted() {
        return VISIBILITY_RESTRICTED.equals(visibility);
    }
}
