package ai.javaclaw.api.admin.skills;

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

/** CRUD endpoints for agent skills. */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillStore store;

    public SkillController(SkillStore store) {
        this.store = store;
    }

    @GetMapping
    public List<SkillDto> list() {
        return store.list();
    }

    @PostMapping
    public ResponseEntity<SkillDto> create(@Valid @RequestBody SkillDto body) {
        return ResponseEntity.status(201).body(store.create(body));
    }

    @PutMapping("/{id}")
    public SkillDto update(@PathVariable String id, @Valid @RequestBody SkillDto body) {
        return store.update(id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        store.delete(id);
        return ResponseEntity.noContent().build();
    }
}
