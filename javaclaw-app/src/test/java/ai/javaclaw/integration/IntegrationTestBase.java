package ai.javaclaw.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared base class for integration tests.
 *
 * <p>Boots the full application context using the {@code contracttest} profile which
 * configures Testcontainers PostgreSQL via the TC JDBC driver, a dummy LLM endpoint,
 * and Flyway migrations.
 *
 * <p>All MockMvc requests default to an authenticated admin user (ROLE_ADMIN)
 * via {@link TestSecurityConfig}. This ensures {@code @Nested} test classes
 * work without explicit auth annotations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("contracttest")
@Import(TestSecurityConfig.class)
abstract class IntegrationTestBase {

    @Autowired
    MockMvc mockMvc;
}
