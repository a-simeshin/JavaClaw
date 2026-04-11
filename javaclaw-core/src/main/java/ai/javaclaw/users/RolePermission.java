package ai.javaclaw.users;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Maps a role to a single permission — read from {@code role_permissions} table.
 */
@Table("role_permissions")
public record RolePermission(@Id Long id, @Column("role") String role, @Column("permission") String permission) {

    public static RolePermission create(String role, String permission) {
        return new RolePermission(null, role, permission);
    }
}
