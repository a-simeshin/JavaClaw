package ai.javaclaw.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * Общий reading-конвертер: десериализует JSON-строку в Map<String,String>.
 *
 * <p>Пустая/null/blank строка → пустая мапа. Используется обоими диалектами.
 */
@ReadingConverter
public final class StringToJsonConverter implements Converter<String, Map<String, String>> {

    private static final TypeReference<Map<String, String>> TYPE = new TypeReference<Map<String, String>>() {};

    private final ObjectMapper objectMapper;

    public StringToJsonConverter(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, String> convert(final String source) {
        if (source == null || source.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(source, TYPE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize JSON to Map<String,String>", e);
        }
    }
}
