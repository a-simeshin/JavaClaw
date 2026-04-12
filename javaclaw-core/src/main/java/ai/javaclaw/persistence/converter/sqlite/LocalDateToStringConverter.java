package ai.javaclaw.persistence.converter.sqlite;

import java.sql.JDBCType;
import java.time.LocalDate;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.mapping.JdbcValue;

/**
 * SQLite writing converter: {@link LocalDate} → ISO-8601 date string ({@code "2026-04-12"}),
 * wrapped in {@link JdbcValue} with explicit {@link JDBCType#VARCHAR} to prevent
 * the JDBC driver from converting to epoch millis.
 */
@WritingConverter
public final class LocalDateToStringConverter implements Converter<LocalDate, JdbcValue> {

    @Override
    public JdbcValue convert(final LocalDate source) {
        final String iso = source == null ? null : source.toString();
        return JdbcValue.of(iso, JDBCType.VARCHAR);
    }
}
