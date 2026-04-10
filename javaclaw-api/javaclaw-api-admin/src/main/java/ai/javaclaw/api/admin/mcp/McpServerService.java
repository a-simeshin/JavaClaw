package ai.javaclaw.api.admin.mcp;

import ai.javaclaw.mcp.McpServer;
import ai.javaclaw.mcp.McpServerRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class McpServerService {

    private final McpServerRepository repository;

    public McpServerService(final McpServerRepository repository) {
        this.repository = repository;
    }

    public List<McpServerDto> list() {
        return repository.findAllByOwnerIdIsNull().stream().map(this::toDto).toList();
    }

    public McpServerDto create(final McpServerDto dto) {
        final McpServer server = McpServer.newGlobal(
                dto.name(), dto.transport(), dto.command(), dto.url(), dto.headers(), dto.enabled());
        return toDto(repository.save(server));
    }

    public McpServerDto update(final String id, final McpServerDto dto) {
        final McpServer current = repository
                .findByIdAndOwnerIdIsNull(id)
                .orElseThrow(() -> new NoSuchElementException("mcp server not found: " + id));
        final McpServer updated =
                current.withUpdate(dto.name(), dto.transport(), dto.command(), dto.url(), dto.headers(), dto.enabled());
        return toDto(repository.save(updated));
    }

    public void delete(final String id) {
        final McpServer server = repository
                .findByIdAndOwnerIdIsNull(id)
                .orElseThrow(() -> new NoSuchElementException("mcp server not found: " + id));
        repository.deleteById(server.id());
    }

    public McpServerStatusDto status(final String id) {
        final McpServer server = repository
                .findByIdAndOwnerIdIsNull(id)
                .orElseThrow(() -> new NoSuchElementException("mcp server not found: " + id));
        if (!server.enabled()) {
            return new McpServerStatusDto(id, "disabled", null);
        }
        final String checkedAt =
                server.lastHealthCheckAt() != null ? server.lastHealthCheckAt().toString() : null;
        return new McpServerStatusDto(id, server.healthStatus(), server.healthDetail(), checkedAt);
    }

    private McpServerDto toDto(final McpServer server) {
        return new McpServerDto(
                server.id(),
                server.name(),
                server.transport(),
                server.command(),
                server.url(),
                server.headers(),
                server.enabled());
    }
}
