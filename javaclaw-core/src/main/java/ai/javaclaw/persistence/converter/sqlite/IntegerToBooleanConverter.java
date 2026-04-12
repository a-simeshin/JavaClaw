package ai.javaclaw.persistence.converter.sqlite;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * SQLite reading converter: INTEGER (0/1) → {@link Boolean}.
 *
 * <p>SQLite has no native BOOLEAN type — boolean columns are stored as INTEGER.
 * The SQLite JDBC driver returns {@link Integer} for such columns, which Spring Data JDBC
 * cannot automatically coerce to {@code boolean}. This converter bridges the gap.
 *
 * <p>Convention: 0 → {@code false}, any non-zero value → {@code true}.
 */
@ReadingConverter
public final class IntegerToBooleanConverter implements Converter<Integer, Boolean> {

    @Override
    public Boolean convert(final Integer source) {
        return source != null && source != 0;
    }
}
