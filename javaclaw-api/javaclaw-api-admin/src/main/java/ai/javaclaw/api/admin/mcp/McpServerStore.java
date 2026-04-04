package ai.javaclaw.api.admin.mcp;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * In-memory MCP server registry. A later task can merge this with
 * {@code McpConnectionsProperties} for durable configuration.
 */
@Component
public class McpServerStore {

    private final ConcurrentMap<String, McpServerDto> items = new ConcurrentHashMap<>();

    public List<McpServerDto> list() {
        return List.copyOf(items.values());
    }

    public McpServerDto create(McpServerDto draft) {
        String id = UUID.randomUUID().toString();
        McpServerDto created = new McpServerDto(
                id,
                draft.name(),
                draft.transport(),
                draft.command(),
                draft.url(),
                draft.headers() != null ? draft.headers() : Map.of(),
                draft.enabled());
        items.put(id, created);
        return created;
    }

    public McpServerDto update(String id, McpServerDto patch) {
        McpServerDto current = items.get(id);
        if (current == null) {
            throw new NoSuchElementException("mcp server not found: " + id);
        }
        McpServerDto updated = new McpServerDto(
                id,
                patch.name() != null ? patch.name() : current.name(),
                patch.transport() != null ? patch.transport() : current.transport(),
                patch.command() != null ? patch.command() : current.command(),
                patch.url() != null ? patch.url() : current.url(),
                patch.headers() != null ? patch.headers() : current.headers(),
                patch.enabled());
        items.put(id, updated);
        return updated;
    }

    public void delete(String id) {
        if (items.remove(id) == null) {
            throw new NoSuchElementException("mcp server not found: " + id);
        }
    }

    public McpServerStatusDto status(String id) {
        McpServerDto server = items.get(id);
        if (server == null) {
            throw new NoSuchElementException("mcp server not found: " + id);
        }
        String status = server.enabled() ? "connected" : "disabled";
        return new McpServerStatusDto(id, status, null);
    }
}
