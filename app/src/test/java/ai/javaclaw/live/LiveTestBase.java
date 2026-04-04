package ai.javaclaw.live;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for live integration tests that use a real LLM via OpenRouter.
 *
 * <p>Tests extending this class are automatically skipped when the
 * {@code OPENROUTER_API_KEY} environment variable is not set.
 *
 * <p>A PostgreSQL Testcontainer is started once and shared across all test
 * classes in the same JVM via the singleton container pattern. The container
 * is started eagerly in a static initializer and stays alive until the JVM
 * shuts down, avoiding restart issues when multiple test classes inherit
 * from this base.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("openrouter")
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
abstract class LiveTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("javaclaw")
            .withUsername("javaclaw")
            .withPassword("javaclaw");

    static {
        postgres.start();
    }
}
