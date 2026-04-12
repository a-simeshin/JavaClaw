package ai.javaclaw.persistence.converter.postgres;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/** Converts PostgreSQL PGobject(jsonb) to Map&lt;String, String&gt; for reading. */
@ReadingConverter
public class JsonbToMapConverter implements Converter<PGobject, Map<String, String>> {

    private final ObjectMapper objectMapper;

    public JsonbToMapConverter(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, String> convert(final PGobject source) {
        if (source == null || source.getValue() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(source.getValue(), new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to convert JSONB to Map", e);
        }
    }
}
