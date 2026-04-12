package ai.javaclaw.persistence.converter.sqlite;

import java.sql.JDBCType;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.mapping.JdbcValue;

/**
 * SQLite-only writing-конвертер: {@link Instant} → ISO-8601 UTC, обёрнутый в
 * {@link JdbcValue} с явным {@link JDBCType#VARCHAR}.
 *
 * <p>Формат {@code 2026-04-11T12:34:56Z} (или с наносекундами —
 * {@code 2026-04-11T12:34:56.123456789Z}) гарантируется
 * {@link DateTimeFormatter#ISO_INSTANT}. Суффикс — всегда {@code Z}, то есть UTC;
 * никаких оффсетов, никаких локальных зон. Такие строки лексикографически
 * сортируются как хронология, что важно для SQLite, где {@code TIMESTAMPTZ}
 * отсутствует и все временные колонки — обычный {@code TEXT}.
 *
 * <p><b>Почему JdbcValue а не просто String:</b> Spring Data JDBC резолвит
 * примитивный тип для {@link Instant} в {@link java.sql.Timestamp} ДО того,
 * как применяются user converters, и биндит параметр как {@link JDBCType#TIMESTAMP}.
 * sqlite-jdbc на TEXT-колонке тогда сериализует epoch-millis — и обратный
 * round-trip через {@code StringToInstantConverter} ломается. Возврат
 * {@link JdbcValue} с явным {@code JDBCType.VARCHAR} перехватывает биндинг и
 * заставляет sqlite-jdbc писать чистую ISO-строку. См. {@code MapToJsonbConverter}
 * — тот же паттерн для Postgres JSONB.
 *
 * <p>Null-safe: {@code null} на входе → {@code JdbcValue.of(null, VARCHAR)}.
 */
@WritingConverter
public final class InstantToStringConverter implements Converter<Instant, JdbcValue> {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    @Override
    public JdbcValue convert(final Instant source) {
        final String iso = source == null ? null : ISO.format(source);
        return JdbcValue.of(iso, JDBCType.VARCHAR);
    }
}
