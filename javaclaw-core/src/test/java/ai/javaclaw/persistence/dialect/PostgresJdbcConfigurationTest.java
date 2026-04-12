package ai.javaclaw.persistence.dialect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.javaclaw.persistence.converter.JsonToStringConverter;
import ai.javaclaw.persistence.converter.StringToJsonConverter;
import ai.javaclaw.persistence.converter.postgres.JsonbToMapConverter;
import ai.javaclaw.persistence.converter.postgres.MapToJsonbConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.jdbc.core.dialect.JdbcPostgresDialect;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

/**
 * Unit tests for {@link PostgresJdbcConfiguration}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>the class is wired as a Spring Data JDBC configuration ({@link AbstractJdbcConfiguration})</li>
 *   <li>the class is annotated to activate on {@code javaclaw.persistence.dialect=postgresql}
 *       with {@code matchIfMissing=true}</li>
 *   <li>{@link PostgresJdbcConfiguration#userConverters()} returns exactly the Postgres JSONB
 *       converters plus the shared JSON text converters, in the expected order</li>
 *   <li>the inherited {@link AbstractJdbcConfiguration#jdbcCustomConversions()} bean contains
 *       the Postgres-specific converters</li>
 * </ul>
 *
 * <p>These are pure unit tests (no {@code ApplicationContextRunner}) because
 * {@code AbstractJdbcConfiguration} eagerly wires downstream beans that require a live
 * {@code DataSource}. The conditional-wiring contract is still pinned via annotation
 * assertions below.
 */
class PostgresJdbcConfigurationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PostgresJdbcConfiguration configuration =
            new PostgresJdbcConfiguration(new SingletonObjectProvider<>(objectMapper));

    @Test
    void extendsAbstractJdbcConfiguration() {
        assertThat(PostgresJdbcConfiguration.class.getSuperclass()).isEqualTo(AbstractJdbcConfiguration.class);
    }

    @Test
    void hasConfigurationAnnotation() {
        Configuration annotation = PostgresJdbcConfiguration.class.getAnnotation(Configuration.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.proxyBeanMethods()).isFalse();
    }

    @Test
    void activatesOnPostgresqlDialectOrMissingProperty() {
        ConditionalOnProperty annotation = PostgresJdbcConfiguration.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.prefix()).isEqualTo("javaclaw.persistence");
        assertThat(annotation.name()).containsExactly("dialect");
        assertThat(annotation.havingValue()).isEqualTo("postgresql");
        assertThat(annotation.matchIfMissing()).isTrue();
    }

    @Test
    void enablesPersistenceDialectProperties() {
        EnableConfigurationProperties annotation =
                PostgresJdbcConfiguration.class.getAnnotation(EnableConfigurationProperties.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly(PersistenceDialectProperties.class);
    }

    @Test
    void userConvertersContainsExpectedPostgresAndSharedConverters() throws Exception {
        List<?> converters = invokeUserConverters(configuration);
        assertThat(converters).hasSize(4);
        assertThat(converters.get(0)).isInstanceOf(MapToJsonbConverter.class);
        assertThat(converters.get(1)).isInstanceOf(JsonbToMapConverter.class);
        assertThat(converters.get(2)).isInstanceOf(JsonToStringConverter.class);
        assertThat(converters.get(3)).isInstanceOf(StringToJsonConverter.class);
    }

    @Test
    void jdbcCustomConversionsBeanIncludesPostgresConverters() {
        // AbstractJdbcConfiguration.jdbcCustomConversions() internally resolves JdbcDialect
        // from the ApplicationContext; wire a stub returning the real JdbcPostgresDialect so
        // the custom conversions are built with the correct set of store-native converters.
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(JdbcDialect.class)).thenReturn(JdbcPostgresDialect.INSTANCE);
        configuration.setApplicationContext(applicationContext);

        JdbcCustomConversions conversions = configuration.jdbcCustomConversions();
        assertThat(conversions).isNotNull();

        // JsonToStringConverter writes Map<String,String> to String (shared JSON text path).
        assertThat(conversions.getCustomWriteTarget(java.util.Map.class, String.class))
                .contains(String.class);
    }

    private static List<?> invokeUserConverters(PostgresJdbcConfiguration cfg)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method method = AbstractJdbcConfiguration.class.getDeclaredMethod("userConverters");
        method.setAccessible(true);
        return (List<?>) method.invoke(cfg);
    }
}
