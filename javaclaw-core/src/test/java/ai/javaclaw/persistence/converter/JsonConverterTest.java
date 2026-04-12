package ai.javaclaw.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonConverterTest {

    private ObjectMapper objectMapper;
    private JsonToStringConverter writer;
    private StringToJsonConverter reader;

    @BeforeEach
    void setUp() {
        this.objectMapper = new ObjectMapper();
        this.writer = new JsonToStringConverter(objectMapper);
        this.reader = new StringToJsonConverter(objectMapper);
    }

    @Test
    @DisplayName("null на вход writer → null")
    void writer_nullIn_returnsNull() {
        assertThat(writer.convert(null)).isNull();
    }

    @Test
    @DisplayName("пустая мапа сериализуется в {} и читается обратно как пустая")
    void emptyMap_roundTrip() {
        String json = writer.convert(Map.of());
        assertThat(json).isEqualTo("{}");
        assertThat(reader.convert(json)).isEmpty();
    }

    @Test
    @DisplayName("round-trip для типичного mcp_servers.headers")
    void headersMap_roundTrip() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer xyz");
        headers.put("X-Api-Version", "2025-01");
        String json = writer.convert(headers);
        Map<String, String> back = reader.convert(json);
        assertThat(back).containsExactlyInAnyOrderEntriesOf(headers);
    }

    @Test
    @DisplayName("unicode-ключи и значения переживают round-trip")
    void unicode_roundTrip() {
        Map<String, String> source = Map.of("ключ", "значение", "emoji", "rocket", "mixed", "Привет, world!");
        Map<String, String> back = reader.convert(writer.convert(source));
        assertThat(back).isEqualTo(source);
    }

    @Test
    @DisplayName("спецсимволы экранируются корректно")
    void specialCharacters_roundTrip() {
        Map<String, String> source = Map.of(
                "quoted", "He said \"hi\"",
                "slash", "a\\b",
                "multiline", "line1\nline2\ttabbed");
        Map<String, String> back = reader.convert(writer.convert(source));
        assertThat(back).isEqualTo(source);
    }

    @Test
    @DisplayName("null/пустая/blank на reader → пустая мапа")
    void reader_nullOrBlank_returnsEmptyMap() {
        assertThat(reader.convert(null)).isEmpty();
        assertThat(reader.convert("")).isEmpty();
        assertThat(reader.convert("   ")).isEmpty();
    }

    @Test
    @DisplayName("некорректный JSON → IllegalStateException")
    void reader_invalidJson_throws() {
        assertThatThrownBy(() -> reader.convert("{not-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to deserialize");
    }
}
