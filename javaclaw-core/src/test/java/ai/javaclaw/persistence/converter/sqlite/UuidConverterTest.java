package ai.javaclaw.persistence.converter.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Round-trip тесты для пары {@link UuidToStringConverter} / {@link StringToUuidConverter}.
 */
class UuidConverterTest {

    private final UuidToStringConverter writer = new UuidToStringConverter();
    private final StringToUuidConverter reader = new StringToUuidConverter();

    @Test
    @DisplayName("null-safe: null → null для обоих направлений")
    void nullSafety() {
        assertThat(writer.convert(null)).isNull();
        assertThat(reader.convert(null)).isNull();
    }

    @Test
    @DisplayName("пустая/blank строка на reader → null")
    void reader_blank_returnsNull() {
        assertThat(reader.convert("")).isNull();
        assertThat(reader.convert("   ")).isNull();
    }

    @Test
    @DisplayName("writer возвращает 36-символьную каноническую форму")
    void writer_canonicalForm() {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        String s = writer.convert(id);

        assertThat(s).isEqualTo("123e4567-e89b-12d3-a456-426614174000").hasSize(36);
    }

    @Test
    @DisplayName("round-trip сохраняет исходный UUID")
    void roundTrip() {
        UUID original = UUID.randomUUID();

        UUID back = reader.convert(writer.convert(original));

        assertThat(back).isEqualTo(original);
    }

    @Test
    @DisplayName("reader на некорректной строке бросает IllegalArgumentException")
    void reader_invalidString_throws() {
        assertThatThrownBy(() -> reader.convert("not-a-uuid")).isInstanceOf(IllegalArgumentException.class);
    }
}
