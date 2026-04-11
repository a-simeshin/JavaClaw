package ai.javaclaw.mcp;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface McpServerRepository extends ListCrudRepository<McpServer, String> {

    List<McpServer> findAllByOwnerIdIsNull();

    Optional<McpServer> findByIdAndOwnerIdIsNull(String id);

    List<McpServer> findAllByEnabledTrue();

    List<McpServer> findAllByOwnerId(String ownerId);

    Optional<McpServer> findByIdAndOwnerId(String id, String ownerId);
}
