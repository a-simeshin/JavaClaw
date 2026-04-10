package ai.javaclaw.users;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Read-only projection of the {@code users} table for resolving
 * authenticated usernames to their database IDs.
 */
@Table("users")
public record AppUser(@Id String id, @Column("username") String username, @Column("role") String role) {}
