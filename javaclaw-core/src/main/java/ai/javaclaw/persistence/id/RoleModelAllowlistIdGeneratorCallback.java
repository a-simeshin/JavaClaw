package ai.javaclaw.persistence.id;

import ai.javaclaw.agent.config.RoleModelAllowlist;
import java.util.UUID;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Генератор идентификаторов для {@link RoleModelAllowlist}.
 *
 * <p>Присваивает случайный UUID записи role-model allowlist, если {@code id} не задан.
 * Обеспечивает переносимость генерации ID между диалектами БД.
 */
@Component
public class RoleModelAllowlistIdGeneratorCallback implements BeforeConvertCallback<RoleModelAllowlist> {

    @Override
    public RoleModelAllowlist onBeforeConvert(final RoleModelAllowlist entry) {
        if (entry.id() == null) {
            return new RoleModelAllowlist(
                    UUID.randomUUID().toString(), entry.role(), entry.modelId(), entry.createdAt());
        }
        return entry;
    }
}
