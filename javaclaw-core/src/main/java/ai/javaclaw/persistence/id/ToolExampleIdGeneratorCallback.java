package ai.javaclaw.persistence.id;

import ai.javaclaw.agent.pipeline.ToolExample;
import java.util.UUID;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Генератор идентификаторов для {@link ToolExample}.
 *
 * <p>Если {@code id} не задан, присваивает случайный UUID, чтобы сохранение
 * few-shot-примеров не зависело от DB-дефолта.
 */
@Component
public class ToolExampleIdGeneratorCallback implements BeforeConvertCallback<ToolExample> {

    @Override
    public ToolExample onBeforeConvert(final ToolExample example) {
        if (example.id() == null) {
            return new ToolExample(
                    UUID.randomUUID().toString(),
                    example.toolName(),
                    example.ownerId(),
                    example.exampleOrder(),
                    example.userMessage(),
                    example.assistantMessage(),
                    example.toolCall(),
                    example.toolResult());
        }
        return example;
    }
}
