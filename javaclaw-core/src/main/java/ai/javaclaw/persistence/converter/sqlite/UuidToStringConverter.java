package ai.javaclaw.persistence.converter.sqlite;

import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

/**
 * SQLite-only writing-конвертер: {@link UUID} → каноническая строка (36 символов).
 *
 * <p>SQLite не имеет нативного UUID-типа: все идентификаторы хранятся в колонках
 * {@code TEXT}. Конвертер нужен, чтобы Spring Data JDBC при записи сущностей
 * производил строковое представление вида {@code 123e4567-e89b-12d3-a456-426614174000}.
 *
 * <p>В Postgres-профиле не используется — {@code pgjdbc} маппит {@code UUID}
 * нативно.
 *
 * <p>Null-safe: {@code null} на входе → {@code null} на выходе.
 */
@WritingConverter
public final class UuidToStringConverter implements Converter<UUID, String> {

    @Override
    public String convert(final UUID source) {
        return source == null ? null : source.toString();
    }
}
