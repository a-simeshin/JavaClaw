package ai.javaclaw.api.admin.toolexamples;

import ai.javaclaw.agent.pipeline.ToolExample;
import ai.javaclaw.agent.pipeline.ToolExampleService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin REST controller for managing few-shot tool examples.
 */
@RestController
@RequestMapping("/api/tool-examples")
public class ToolExampleController {

    private final ToolExampleService service;

    public ToolExampleController(final ToolExampleService service) {
        this.service = service;
    }

    @GetMapping
    public List<ToolExample> list(@RequestParam(required = false) final String toolName) {
        return service.findAll(toolName);
    }

    @GetMapping("/{id}")
    public ToolExample get(@PathVariable final String id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<ToolExample> create(@Valid @RequestBody final ToolExampleRequest req) {
        final ToolExample created = service.create(
                req.toolName(),
                req.ownerId(),
                req.exampleOrder(),
                req.userMessage(),
                req.assistantMessage(),
                req.toolCall(),
                req.toolResult());
        return ResponseEntity.status(201).body(created);
    }

    @PutMapping("/{id}")
    public ToolExample update(@PathVariable final String id, @Valid @RequestBody final ToolExampleRequest req) {
        return service.update(
                id,
                req.toolName(),
                req.exampleOrder(),
                req.userMessage(),
                req.assistantMessage(),
                req.toolCall(),
                req.toolResult());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable final String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
