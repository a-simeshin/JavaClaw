package ai.javaclaw.agent.pipeline;

import java.util.List;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing few-shot tool examples used to improve tool-calling accuracy.
 *
 * <p>Provides CRUD operations on top of {@link ToolExampleRepository}. Read methods
 * operate on global (owner_id IS NULL) examples by default.
 */
@Service
public class ToolExampleService {

    private final ToolExampleRepository repository;

    public ToolExampleService(final ToolExampleRepository repository) {
        this.repository = repository;
    }

    public List<ToolExample> findAll(@Nullable final String toolName) {
        if (toolName != null && !toolName.isBlank()) {
            return repository.findGlobalByToolName(toolName);
        }
        return repository.findAllGlobal();
    }

    public ToolExample findById(final String id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("ToolExample not found: " + id));
    }

    @Transactional
    public ToolExample create(
            final String toolName,
            @Nullable final String ownerId,
            final int exampleOrder,
            final String userMessage,
            @Nullable final String assistantMessage,
            final String toolCall,
            @Nullable final String toolResult) {
        return repository.save(ToolExample.create(
                toolName, ownerId, exampleOrder, userMessage, assistantMessage, toolCall, toolResult));
    }

    @Transactional
    public ToolExample update(
            final String id,
            final String toolName,
            final int exampleOrder,
            final String userMessage,
            @Nullable final String assistantMessage,
            final String toolCall,
            @Nullable final String toolResult) {
        final ToolExample existing = findById(id);
        final ToolExample updated = new ToolExample(
                id, toolName, existing.ownerId(), exampleOrder, userMessage, assistantMessage, toolCall, toolResult);
        return repository.save(updated);
    }

    @Transactional
    public void delete(final String id) {
        repository.deleteById(id);
    }
}
