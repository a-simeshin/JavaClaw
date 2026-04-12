package ai.javaclaw.persistence.dialect;

import ai.javaclaw.persistence.converter.JsonToStringConverter;
import ai.javaclaw.persistence.converter.StringToJsonConverter;
import ai.javaclaw.persistence.converter.sqlite.BooleanToIntegerConverter;
import ai.javaclaw.persistence.converter.sqlite.InstantToStringConverter;
import ai.javaclaw.persistence.converter.sqlite.IntegerToBooleanConverter;
import ai.javaclaw.persistence.converter.sqlite.LocalDateToStringConverter;
import ai.javaclaw.persistence.converter.sqlite.StringToInstantConverter;
import ai.javaclaw.persistence.converter.sqlite.StringToLocalDateConverter;
import ai.javaclaw.persistence.converter.sqlite.StringToUuidConverter;
import ai.javaclaw.persistence.converter.sqlite.UuidToStringConverter;
import ai.javaclaw.persistence.dialect.sqlite.SqliteJdbcDialect;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

/**
 * SQLite-specific Spring Data JDBC configuration.
 *
 * <p>Active when {@code javaclaw.persistence.dialect=sqlite}. Provides the custom
 * {@link SqliteJdbcDialect} (bypassing Spring Data JDBC auto-detection, which
 * defaults SQLite to {@code AnsiDialect} and emits incompatible LIMIT/OFFSET
 * syntax) plus SQLite-only UUID/Instant and shared JSON converters. Postgres
 * JSONB converters are intentionally omitted.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PersistenceDialectProperties.class)
@ConditionalOnProperty(prefix = "javaclaw.persistence", name = "dialect", havingValue = "sqlite")
public class SqliteJdbcConfiguration extends AbstractJdbcConfiguration {

    /** ObjectMapper used by JSON converters. */
    private final ObjectMapper objectMapper;

    /**
     * Accepts an {@link ObjectProvider} so the configuration works under both the full app
     * context (where {@code JacksonAutoConfiguration} publishes an {@code ObjectMapper}) and
     * the {@code @DataJdbcTest} slice, which does not pull Jackson autoconfig. The fallback is
     * a stock {@code new ObjectMapper()} — sufficient for the {@code Map<String,String>}
     * round-trips performed by the shared JSON converters.
     */
    public SqliteJdbcConfiguration(ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
    }

    @Override
    public JdbcDialect jdbcDialect(NamedParameterJdbcOperations operations) {
        return SqliteJdbcDialect.INSTANCE;
    }

    @Override
    protected List<?> userConverters() {
        return List.of(
                new UuidToStringConverter(),
                new StringToUuidConverter(),
                new InstantToStringConverter(),
                new StringToInstantConverter(),
                new LocalDateToStringConverter(),
                new StringToLocalDateConverter(),
                new BooleanToIntegerConverter(),
                new IntegerToBooleanConverter(),
                new JsonToStringConverter(objectMapper),
                new StringToJsonConverter(objectMapper));
    }
}
