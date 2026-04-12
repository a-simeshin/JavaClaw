package ai.javaclaw.persistence.converter.sqlite;

import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * SQLite-only reading-конвертер: каноническая 36-символьная строка → {@link UUID}.
 *
 * <p>Парность к {@link UuidToStringConverter}: SQLite хранит идентификаторы
 * в {@code TEXT}-колонках, и при чтении Spring Data JDBC получает {@link String},
 * который нужно превратить обратно в {@link UUID}.
 *
 * <p>Null/пустая строка → {@code null}. Некорректный формат пробрасывает
 * {@link IllegalArgumentException} из {@link UUID#fromString(String)} — это
 * «fail fast»: значит, в БД попал мусор.
 */
@ReadingConverter
public final class StringToUuidConverter implements Converter<String, UUID> {

    @Override
    public UUID convert(final String source) {
        return (source == null || source.isBlank()) ? null : UUID.fromString(source);
    }
}
