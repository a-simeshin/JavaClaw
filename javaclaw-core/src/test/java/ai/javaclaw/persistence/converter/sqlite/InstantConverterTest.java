package ai.javaclaw.persistence.converter.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.JDBCType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jdbc.core.mapping.JdbcValue;

/**
 * Round-trip тесты для пары {@link InstantToStringConverter} / {@link StringToInstantConverter}.
 *
 * <p>Writer теперь возвращает {@link JdbcValue} с явным {@link JDBCType#VARCHAR},
 * чтобы перехватить биндинг Spring Data JDBC и заставить sqlite-jdbc писать
 * ISO-8601 строку, а не epoch-millis. См. javadoc {@link InstantToStringConverter}.
 */
class InstantConverterTest {

    private final InstantToStringConverter writer = new InstantToStringConverter();
    private final StringToInstantConverter reader = new StringToInstantConverter();

    /** Распаковывает значение из {@link JdbcValue}. */
    private String write(final Instant source) {
        final JdbcValue jv = writer.convert(source);
        return jv == null ? null : (String) jv.getValue();
    }

    @Test
    @DisplayName("null-safe: null → JdbcValue(null, VARCHAR) для writer; null → null для reader")
    void nullSafety() {
        final JdbcValue nullWrite = writer.convert(null);
        assertThat(nullWrite).isNotNull();
        assertThat(nullWrite.getValue()).isNull();
        assertThat(nullWrite.getJdbcType()).isEqualTo(JDBCType.VARCHAR);

        assertThat(reader.convert(null)).isNull();
    }

    @Test
    @DisplayName("writer всегда возвращает VARCHAR sqlType")
    void writer_alwaysVarcharSqlType() {
        assertThat(writer.convert(Instant.EPOCH).getJdbcType()).isEqualTo(JDBCType.VARCHAR);
    }

    @Test
    @DisplayName("пустая/blank строка на reader → null")
    void reader_blank_returnsNull() {
        assertThat(reader.convert("")).isNull();
        assertThat(reader.convert("   ")).isNull();
    }

    @Test
    @DisplayName("Instant.EPOCH сериализуется как 1970-01-01T00:00:00Z")
    void epoch_writesAsIso() {
        final String s = write(Instant.EPOCH);

        assertThat(s).isEqualTo("1970-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("round-trip для EPOCH сохраняет инстант")
    void epoch_roundTrip() {
        final Instant back = reader.convert(write(Instant.EPOCH));

        assertThat(back).isEqualTo(Instant.EPOCH);
    }

    @Test
    @DisplayName("наносекундная точность переживает round-trip")
    void nanoseconds_roundTrip() {
        final Instant source = Instant.parse("2026-04-11T12:34:56.123456789Z");

        final String serialized = write(source);
        final Instant back = reader.convert(serialized);

        assertThat(serialized).isEqualTo("2026-04-11T12:34:56.123456789Z");
        assertThat(back).isEqualTo(source);
    }

    @Test
    @DisplayName("writer всегда нормализует к UTC с суффиксом Z")
    void writer_normalizesToUtc() {
        // исходный момент времени (независимо от зоны) должен
        // сериализоваться в строку с суффиксом Z.
        final Instant source = ZonedDateTime.of(2026, 4, 11, 15, 0, 0, 0, ZoneOffset.ofHours(3))
                .toInstant();

        final String s = write(source);

        assertThat(s).endsWith("Z").isEqualTo("2026-04-11T12:00:00Z");
    }

    @Test
    @DisplayName("round-trip для произвольного момента времени")
    void arbitraryInstant_roundTrip() {
        final Instant source = Instant.parse("2026-04-11T12:34:56Z");

        final Instant back = reader.convert(write(source));

        assertThat(back).isEqualTo(source);
    }

    @Test
    @DisplayName("reader на некорректной строке бросает DateTimeParseException")
    void reader_invalidString_throws() {
        assertThatThrownBy(() -> reader.convert("not-a-timestamp")).isInstanceOf(DateTimeParseException.class);
    }
}
