package ai.javaclaw.e2e.support;

import ai.javaclaw.JavaClawApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for non-browser integration tests. Boots the full Spring application
 * context (via {@link JavaClawApplication}) with a shared PostgreSQL Testcontainer.
 *
 * <p>Use {@code MockMvc} / {@code WebTestClient} or {@code TestRestTemplate} to call
 * the HTTP endpoints. No real LLM calls are made; tests should mock or bypass the
 * agent layer as needed.
 */
@SpringBootTest(classes = JavaClawApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = PostgresContainer.INSTANCE;
}
