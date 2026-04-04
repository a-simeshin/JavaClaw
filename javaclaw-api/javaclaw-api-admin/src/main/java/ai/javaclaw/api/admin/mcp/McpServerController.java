package ai.javaclaw.api.admin.mcp;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD + status endpoints for MCP server registrations. */
@RestController
@RequestMapping("/api/mcp-servers")
public class McpServerController {

    private final McpServerStore store;

    public McpServerController(McpServerStore store) {
        this.store = store;
    }

    @GetMapping
    public List<McpServerDto> list() {
        return store.list();
    }

    @PostMapping
    public ResponseEntity<McpServerDto> create(@Valid @RequestBody McpServerDto body) {
        return ResponseEntity.status(201).body(store.create(body));
    }

    @PutMapping("/{id}")
    public McpServerDto update(@PathVariable String id, @Valid @RequestBody McpServerDto body) {
        return store.update(id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        store.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/status")
    public McpServerStatusDto status(@PathVariable String id) {
        return store.status(id);
    }
}
