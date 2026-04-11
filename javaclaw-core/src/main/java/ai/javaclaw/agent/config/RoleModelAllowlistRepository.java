package ai.javaclaw.agent.config;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface RoleModelAllowlistRepository extends CrudRepository<RoleModelAllowlist, String> {

    List<RoleModelAllowlist> findByRole(String role);

    boolean existsByRoleAndModelId(String role, String modelId);

    @Modifying
    @Query("DELETE FROM role_model_allowlist WHERE role = :role AND model_id = :modelId")
    void deleteByRoleAndModelId(@Param("role") String role, @Param("modelId") String modelId);

    @Modifying
    @Query("DELETE FROM role_model_allowlist WHERE role = :role")
    void deleteAllByRole(@Param("role") String role);
}
