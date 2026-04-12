package ai.javaclaw.persistence.dialect;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code javaclaw.persistence.*} properties.
 *
 * <p>Default dialect is {@link PersistenceDialect#POSTGRESQL} — enforced both by
 * the record's compact constructor and by {@code matchIfMissing=true} on the
 * Postgres {@code @ConditionalOnProperty}. Unknown values fail-fast at startup
 * via Spring Boot's configuration binder.
 */
@ConfigurationProperties(prefix = "javaclaw.persistence")
public record PersistenceDialectProperties(PersistenceDialect dialect) {

    public PersistenceDialectProperties {
        if (dialect == null) {
            dialect = PersistenceDialect.POSTGRESQL;
        }
    }
}
