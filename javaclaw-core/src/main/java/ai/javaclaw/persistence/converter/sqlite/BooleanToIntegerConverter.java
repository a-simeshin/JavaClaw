package ai.javaclaw.persistence.converter.sqlite;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

/**
 * SQLite writing converter: {@link Boolean} → INTEGER (0/1).
 *
 * <p>Pair to {@link IntegerToBooleanConverter}. Writes Java {@code boolean} values
 * as SQLite INTEGER so they survive the round-trip through the JDBC driver.
 */
@WritingConverter
public final class BooleanToIntegerConverter implements Converter<Boolean, Integer> {

    @Override
    public Integer convert(final Boolean source) {
        return (source != null && source) ? 1 : 0;
    }
}
