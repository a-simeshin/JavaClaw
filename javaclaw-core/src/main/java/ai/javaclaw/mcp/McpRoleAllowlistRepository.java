package ai.javaclaw.mcp;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface McpRoleAllowlistRepository extends ListCrudRepository<McpRoleAllowlist, String> {

    List<McpRoleAllowlist> findByServerId(String serverId);

    List<McpRoleAllowlist> findByRole(String role);

    boolean existsByServerIdAndRole(String serverId, String role);

    @Modifying
    @Query("DELETE FROM mcp_role_allowlist WHERE server_id = :serverId AND role = :role")
    void deleteByServerIdAndRole(String serverId, String role);

    @Modifying
    @Query("DELETE FROM mcp_role_allowlist WHERE server_id = :serverId")
    void deleteAllByServerId(String serverId);
}
