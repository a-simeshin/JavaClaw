package ai.javaclaw.persistence.id;

import ai.javaclaw.users.AppUser;
import java.util.UUID;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Генератор идентификаторов для {@link AppUser}.
 *
 * <p>Вызывается Spring Data JDBC перед сохранением сущности. Если {@code id} отсутствует,
 * присваивается новый UUID; иначе сущность возвращается без изменений. Такой подход
 * делает генерацию идентификаторов переносимой между диалектами БД и не зависит от
 * {@code DEFAULT gen_random_uuid()} на стороне PostgreSQL.
 */
@Component
public class AppUserIdGeneratorCallback implements BeforeConvertCallback<AppUser> {

    @Override
    public AppUser onBeforeConvert(final AppUser user) {
        if (user.id() == null) {
            return new AppUser(
                    UUID.randomUUID().toString(), user.username(), user.passwordHash(), user.role(), user.active());
        }
        return user;
    }
}
