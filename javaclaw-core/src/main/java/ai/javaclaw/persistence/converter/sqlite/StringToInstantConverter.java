package ai.javaclaw.persistence.converter.sqlite;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * SQLite-only reading-конвертер: ISO-8601 UTC строка → {@link Instant}.
 *
 * <p>Парность к {@link InstantToStringConverter}. Ожидаемый формат — то, что
 * пишет {@link DateTimeFormatter#ISO_INSTANT}: обязательный суффикс {@code Z}.
 * {@link Instant#from(java.time.temporal.TemporalAccessor)} нормализует любое
 * представление в UTC-инстант (наносекунды сохраняются).
 *
 * <p>Null/пустая строка → {@code null}. Некорректный формат пробрасывает
 * {@link java.time.format.DateTimeParseException} — «fail fast».
 */
@ReadingConverter
public final class StringToInstantConverter implements Converter<String, Instant> {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    @Override
    public Instant convert(final String source) {
        return (source == null || source.isBlank()) ? null : Instant.from(ISO.parse(source));
    }
}
