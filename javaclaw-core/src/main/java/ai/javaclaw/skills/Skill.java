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
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt) {

    public static Skill newGlobal(final String name, final String description, final boolean enabled) {
        return new Skill(null, null, name, description, null, enabled, Instant.now(), Instant.now());
    }

    public Skill withPatch(final String patchName, final String patchDescription, final boolean patchEnabled) {
        return new Skill(
                this.id,
                this.ownerId,
                patchName != null ? patchName : this.name,
                patchDescription != null ? patchDescription : this.description,
                this.content,
                patchEnabled,
                this.createdAt,
                Instant.now());
    }
}
