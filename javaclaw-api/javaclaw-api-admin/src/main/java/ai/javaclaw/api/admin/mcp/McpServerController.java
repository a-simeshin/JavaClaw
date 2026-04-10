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

    private final McpServerService service;

    public McpServerController(final McpServerService service) {
        this.service = service;
    }

    @GetMapping
    public List<McpServerDto> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<McpServerDto> create(@Valid @RequestBody final McpServerDto body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PutMapping("/{id}")
    public McpServerDto update(@PathVariable final String id, @Valid @RequestBody final McpServerDto body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable final String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/status")
    public McpServerStatusDto status(@PathVariable final String id) {
        return service.status(id);
    }

    @GetMapping("/tools")
    public ToolCacheInfoDto tools() {
        return service.toolCacheInfo();
    }
}
