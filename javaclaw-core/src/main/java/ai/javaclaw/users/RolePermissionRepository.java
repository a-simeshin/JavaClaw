package ai.javaclaw.users;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC repository for {@code role_permissions} table.
 */
public interface RolePermissionRepository extends ListCrudRepository<RolePermission, Long> {

    @Query("SELECT * FROM role_permissions WHERE role = :role ORDER BY permission")
    List<RolePermission> findByRole(@Param("role") String role);

    @Query("SELECT DISTINCT permission FROM role_permissions WHERE role = :role ORDER BY permission")
    List<String> findPermissionsByRole(@Param("role") String role);

    @Query("SELECT COUNT(*) > 0 FROM role_permissions WHERE role = :role AND permission = :permission")
    boolean existsByRoleAndPermission(@Param("role") String role, @Param("permission") String permission);

    @Modifying
    @Query("DELETE FROM role_permissions WHERE role = :role AND permission = :permission")
    void deleteByRoleAndPermission(@Param("role") String role, @Param("permission") String permission);

    @Modifying
    @Query("DELETE FROM role_permissions WHERE role = :role")
    void deleteAllByRole(@Param("role") String role);

    @Query("SELECT DISTINCT role FROM role_permissions ORDER BY role")
    List<String> findDistinctRoles();
}
