package ai.javaclaw.persistence.dialect;

/**
 * Supported persistence dialects for JavaClaw.
 *
 * <p>Binds to {@code javaclaw.persistence.dialect} configuration property via
 * {@link PersistenceDialectProperties}. Default is {@link #POSTGRESQL}.
 */
public enum PersistenceDialect {
    POSTGRESQL,
    SQLITE
}
