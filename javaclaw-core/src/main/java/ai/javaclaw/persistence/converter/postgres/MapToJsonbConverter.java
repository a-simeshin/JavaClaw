package ai.javaclaw.persistence.converter.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.JDBCType;
import java.util.Map;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.mapping.JdbcValue;

/** Converts Map&lt;String, String&gt; to JdbcValue(jsonb) for writing to a PostgreSQL JSONB column. */
@WritingConverter
public class MapToJsonbConverter implements Converter<Map<String, String>, JdbcValue> {

    private final ObjectMapper objectMapper;

    public MapToJsonbConverter(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public JdbcValue convert(final Map<String, String> source) {
        try {
            final PGobject pgObject = new PGobject();
            pgObject.setType("jsonb");
            pgObject.setValue(objectMapper.writeValueAsString(source));
            return JdbcValue.of(pgObject, JDBCType.OTHER);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to convert Map to JSONB", e);
        }
    }
}
