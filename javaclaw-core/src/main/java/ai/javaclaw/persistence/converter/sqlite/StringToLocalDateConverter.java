package ai.javaclaw.persistence.converter.sqlite;

import java.time.LocalDate;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * SQLite reading converter: ISO-8601 date string ({@code "2026-04-12"}) → {@link LocalDate}.
 */
@ReadingConverter
public final class StringToLocalDateConverter implements Converter<String, LocalDate> {

    @Override
    public LocalDate convert(final String source) {
        return source == null || source.isBlank() ? null : LocalDate.parse(source);
    }
}
