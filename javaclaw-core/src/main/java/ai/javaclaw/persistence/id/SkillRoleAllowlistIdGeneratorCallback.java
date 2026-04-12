package ai.javaclaw.persistence.id;

import ai.javaclaw.skills.SkillRoleAllowlist;
import java.util.UUID;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Генератор идентификаторов для {@link SkillRoleAllowlist}.
 *
 * <p>Проставляет случайный UUID для записи allowlist, если {@code id} не задан.
 * Таблица {@code skill_role_allowlist} имеет {@code DEFAULT gen_random_uuid()} в DDL,
 * но для переносимости на SQLite и прочие диалекты генерация выполняется на стороне Java.
 */
@Component
public class SkillRoleAllowlistIdGeneratorCallback implements BeforeConvertCallback<SkillRoleAllowlist> {

    @Override
    public SkillRoleAllowlist onBeforeConvert(final SkillRoleAllowlist entry) {
        if (entry.id() == null) {
            return new SkillRoleAllowlist(
                    UUID.randomUUID().toString(), entry.skillId(), entry.role(), entry.createdAt());
        }
        return entry;
    }
}
