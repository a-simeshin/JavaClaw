package ai.javaclaw.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

/**
 * Общий writing-конвертер: сериализует Map<String,String> в JSON-строку.
 *
 * <p>Используется обоими диалектами (SQLite и Postgres) для простых JSON-полей.
 * Для JSONB в Postgres применяется отдельный MapToJsonbConverter.
 *
 * <p>Null-safe: null на входе → null на выходе.
 */
@WritingConverter
public final class JsonToStringConverter implements Converter<Map<String, String>, String> {

    private final ObjectMapper objectMapper;

    public JsonToStringConverter(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convert(final Map<String, String> source) {
        if (source == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(source);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Map<String,String> to JSON", e);
        }
    }
}
