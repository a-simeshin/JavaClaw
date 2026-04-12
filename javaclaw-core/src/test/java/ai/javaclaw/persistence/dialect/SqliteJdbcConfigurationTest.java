package ai.javaclaw.persistence.dialect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

/**
 * Unit tests for {@link SqliteJdbcConfiguration}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>the class extends {@link AbstractJdbcConfiguration}</li>
 *   <li>it activates only when {@code javaclaw.persistence.dialect=sqlite}
 *       (no {@code matchIfMissing})</li>
 *   <li>{@link SqliteJdbcConfiguration#jdbcDialect(NamedParameterJdbcOperations)} overrides
 *       the default resolver and returns {@link SqliteJdbcDialect#INSTANCE}</li>
 *   <li>{@code userConverters()} returns the SQLite-only UUID/Instant converters plus the
 *       shared JSON text converters (no Postgres JSONB converters)</li>
 *   <li>the inherited {@code jdbcCustomConversions()} bean is wired from those converters</li>
 * </ul>
 */
class SqliteJdbcConfigurationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SqliteJdbcConfiguration configuration =
            new SqliteJdbcConfiguration(new SingletonObjectProvider<>(objectMapper));

    @Test
    void extendsAbstractJdbcConfiguration() {
        assertThat(SqliteJdbcConfiguration.class.getSuperclass()).isEqualTo(AbstractJdbcConfiguration.class);
    }

    @Test
    void hasConfigurationAnnotation() {
        Configuration annotation = SqliteJdbcConfiguration.class.getAnnotation(Configuration.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.proxyBeanMethods()).isFalse();
    }

    @Test
    void activatesOnlyForSqliteDialect() {
        ConditionalOnProperty annotation = SqliteJdbcConfiguration.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.prefix()).isEqualTo("javaclaw.persistence");
        assertThat(annotation.name()).containsExactly("dialect");
        assertThat(annotation.havingValue()).isEqualTo("sqlite");
        assertThat(annotation.matchIfMissing()).isFalse();
    }

    @Test
    void enablesPersistenceDialectProperties() {
        EnableConfigurationProperties annotation =
                SqliteJdbcConfiguration.class.getAnnotation(EnableConfigurationProperties.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly(PersistenceDialectProperties.class);
    }

    @Test
    void jdbcDialectReturnsSqliteSingleton() {
        NamedParameterJdbcOperations operations = mock(NamedParameterJdbcOperations.class);
        JdbcDialect dialect = configuration.jdbcDialect(operations);
        assertThat(dialect).isSameAs(SqliteJdbcDialect.INSTANCE);
    }

    @Test
    void userConvertersContainsSqliteSpecificAndSharedConverters() throws Exception {
        List<?> converters = invokeUserConverters(configuration);
        assertThat(converters).hasSize(10);
        assertThat(converters.get(0)).isInstanceOf(UuidToStringConverter.class);
        assertThat(converters.get(1)).isInstanceOf(StringToUuidConverter.class);
        assertThat(converters.get(2)).isInstanceOf(InstantToStringConverter.class);
        assertThat(converters.get(3)).isInstanceOf(StringToInstantConverter.class);
        assertThat(converters.get(4)).isInstanceOf(LocalDateToStringConverter.class);
        assertThat(converters.get(5)).isInstanceOf(StringToLocalDateConverter.class);
        assertThat(converters.get(6)).isInstanceOf(BooleanToIntegerConverter.class);
        assertThat(converters.get(7)).isInstanceOf(IntegerToBooleanConverter.class);
        assertThat(converters.get(8)).isInstanceOf(JsonToStringConverter.class);
        assertThat(converters.get(9)).isInstanceOf(StringToJsonConverter.class);
    }

    @Test
    void jdbcCustomConversionsBeanIncludesSqliteConverters() {
        // AbstractJdbcConfiguration.jdbcCustomConversions() internally resolves JdbcDialect
        // from the ApplicationContext; wire a stub returning SqliteJdbcDialect.INSTANCE so the
        // conversions are built with the SQLite store-native converters.
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(JdbcDialect.class)).thenReturn(SqliteJdbcDialect.INSTANCE);
        configuration.setApplicationContext(applicationContext);

        JdbcCustomConversions conversions = configuration.jdbcCustomConversions();
        assertThat(conversions).isNotNull();
        // SQLite-only: UUID → String write converter registered by UuidToStringConverter.
        assertThat(conversions.getCustomWriteTarget(UUID.class, String.class)).contains(String.class);
    }

    private static List<?> invokeUserConverters(SqliteJdbcConfiguration cfg)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method method = AbstractJdbcConfiguration.class.getDeclaredMethod("userConverters");
        method.setAccessible(true);
        return (List<?>) method.invoke(cfg);
    }
}
