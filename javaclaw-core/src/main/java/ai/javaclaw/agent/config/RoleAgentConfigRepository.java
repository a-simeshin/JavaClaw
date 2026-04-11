package ai.javaclaw.agent.config;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Spring Data JDBC repository for role_agent_config table (V36). */
public interface RoleAgentConfigRepository extends ListCrudRepository<RoleAgentConfig, String> {

    Optional<RoleAgentConfig> findByRole(String role);

    boolean existsByRole(String role);

    @Modifying
    @Query("DELETE FROM role_agent_config WHERE role = :role")
    void deleteByRole(@Param("role") String role);

    @Query("SELECT * FROM role_agent_config ORDER BY role")
    List<RoleAgentConfig> findAllOrdered();
}
