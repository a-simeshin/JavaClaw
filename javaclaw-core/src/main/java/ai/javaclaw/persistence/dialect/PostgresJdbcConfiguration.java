package ai.javaclaw.persistence.dialect;

import ai.javaclaw.persistence.converter.JsonToStringConverter;
import ai.javaclaw.persistence.converter.StringToJsonConverter;
import ai.javaclaw.persistence.converter.postgres.JsonbToMapConverter;
import ai.javaclaw.persistence.converter.postgres.MapToJsonbConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

/**
 * PostgreSQL-specific Spring Data JDBC configuration.
 *
 * <p>Active when {@code javaclaw.persistence.dialect=postgresql} (or unset — default).
 * Registers PGobject/JSONB converters for Postgres-only JSONB columns plus the
 * shared JSON text converters. The JDBC dialect itself is auto-detected from the
 * {@code DataSource} metadata, so {@link #jdbcDialect(org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations)}
 * is intentionally not overridden.
 *
 * <p>Note: {@link AbstractJdbcConfiguration#jdbcCustomConversions()} builds a
 * {@code JdbcCustomConversions} bean automatically from {@link #userConverters()},
 * so no explicit {@code @Primary JdbcCustomConversions} bean is declared here.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PersistenceDialectProperties.class)
@ConditionalOnProperty(
        prefix = "javaclaw.persistence",
        name = "dialect",
        havingValue = "postgresql",
        matchIfMissing = true)
public class PostgresJdbcConfiguration extends AbstractJdbcConfiguration {

    private final ObjectMapper objectMapper;

    /**
     * Accepts an {@link ObjectProvider} so the configuration works under both the full app
     * context (where {@code JacksonAutoConfiguration} publishes an {@code ObjectMapper} bean)
     * and the {@code @DataJdbcTest} slice, which does not pull Jackson autoconfig. The
     * fallback is a stock {@code new ObjectMapper()} — sufficient for {@code Map<String,String>}
     * round-trips performed by the JSONB converters.
     */
    public PostgresJdbcConfiguration(ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
    }

    @Override
    protected List<?> userConverters() {
        return List.of(
                // Dialect-specific: PGobject <-> Map (JSONB column on mcp_servers.headers only)
                new MapToJsonbConverter(objectMapper),
                new JsonbToMapConverter(objectMapper),
                // Shared: plain JSON text (portable path for future TEXT columns)
                new JsonToStringConverter(objectMapper),
                new StringToJsonConverter(objectMapper)
                // Instant <-> TIMESTAMPTZ is handled natively by the PostgreSQL JDBC driver.
                // UUID is handled natively by the PostgreSQL JDBC driver.
                );
    }
}
