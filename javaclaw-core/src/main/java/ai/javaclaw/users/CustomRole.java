package ai.javaclaw.users;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Custom role metadata — stored in {@code custom_roles} table.
 * Built-in roles (ADMIN, POWER_USER, USER) are seeded with {@code builtIn = true}.
 */
@Table("custom_roles")
public record CustomRole(
        @Id Long id,
        @Column("name") String name,
        @Column("description") String description,
        @Column("built_in") boolean builtIn,
        @Column("created_at") Instant createdAt) {

    public static CustomRole create(String name, String description) {
        return new CustomRole(null, name, description, false, Instant.now());
    }
}
