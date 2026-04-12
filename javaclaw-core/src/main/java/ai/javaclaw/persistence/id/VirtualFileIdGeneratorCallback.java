package ai.javaclaw.persistence.id;

import ai.javaclaw.files.VirtualFile;
import java.util.UUID;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Генератор идентификаторов для {@link VirtualFile}.
 *
 * <p>Проставляет случайный UUID, если {@code id} не задан. Благодаря этому
 * Spring Data JDBC не зависит от {@code DEFAULT gen_random_uuid()} в DDL и
 * сохранение работает одинаково на PostgreSQL и встроенных диалектах.
 */
@Component
public class VirtualFileIdGeneratorCallback implements BeforeConvertCallback<VirtualFile> {

    @Override
    public VirtualFile onBeforeConvert(final VirtualFile file) {
        if (file.id() == null) {
            return new VirtualFile(
                    UUID.randomUUID().toString(),
                    file.ownerId(),
                    file.path(),
                    file.content(),
                    file.contentType(),
                    file.sizeBytes(),
                    file.createdAt(),
                    file.updatedAt());
        }
        return file;
    }
}
