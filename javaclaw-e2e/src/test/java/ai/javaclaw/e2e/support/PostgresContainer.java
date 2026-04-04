package ai.javaclaw.e2e.support;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Singleton PostgreSQL Testcontainer shared across all integration and E2E tests
 * in the JVM. Using a single container (combined with identical {@code @SpringBootTest}
 * configuration) enables Spring context caching across test classes.
 */
public final class PostgresContainer {

    public static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("javaclaw_e2e")
            .withUsername("javaclaw")
            .withPassword("javaclaw")
            .withReuse(true);

    static {
        INSTANCE.start();
    }

    private PostgresContainer() {}
}
