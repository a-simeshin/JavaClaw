package ai.javaclaw.e2e.support;

import ai.javaclaw.JavaClawApplication;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for live integration tests that use a real LLM via OpenRouter
 * but no browser. Tests are skipped unless {@code OPENROUTER_API_KEY} is set.
 */
@SpringBootTest(classes = JavaClawApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("live")
@Tag("e2e")
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
public abstract class LiveTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = PostgresContainer.INSTANCE;
}
