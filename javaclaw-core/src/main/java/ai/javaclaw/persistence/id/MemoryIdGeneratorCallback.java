package ai.javaclaw.persistence.id;

import ai.javaclaw.memory.Memory;
import java.util.UUID;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Генератор идентификаторов для {@link Memory}.
 *
 * <p>Проставляет случайный UUID, если {@code id} не задан. Обеспечивает переносимость
 * сохранения воспоминаний между диалектами БД.
 */
@Component
public class MemoryIdGeneratorCallback implements BeforeConvertCallback<Memory> {

    @Override
    public Memory onBeforeConvert(final Memory memory) {
        if (memory.id() == null) {
            return new Memory(
                    UUID.randomUUID().toString(),
                    memory.ownerId(),
                    memory.key(),
                    memory.content(),
                    memory.category(),
                    memory.createdAt(),
                    memory.updatedAt());
        }
        return memory;
    }
}
