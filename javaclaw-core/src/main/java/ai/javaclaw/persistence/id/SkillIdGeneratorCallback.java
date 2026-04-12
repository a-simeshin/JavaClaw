package ai.javaclaw.persistence.id;

import ai.javaclaw.skills.Skill;
import java.util.UUID;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Генератор идентификаторов для {@link Skill}.
 *
 * <p>Выставляет случайный UUID новому скиллу, если {@code id} не задан явно. Необходим,
 * чтобы не полагаться на {@code DEFAULT gen_random_uuid()} PostgreSQL и сохранить
 * переносимость на SQLite и другие диалекты.
 */
@Component
public class SkillIdGeneratorCallback implements BeforeConvertCallback<Skill> {

    @Override
    public Skill onBeforeConvert(final Skill skill) {
        if (skill.id() == null) {
            return new Skill(
                    UUID.randomUUID().toString(),
                    skill.ownerId(),
                    skill.name(),
                    skill.description(),
                    skill.content(),
                    skill.enabled(),
                    skill.visibility(),
                    skill.createdAt(),
                    skill.updatedAt());
        }
        return skill;
    }
}
