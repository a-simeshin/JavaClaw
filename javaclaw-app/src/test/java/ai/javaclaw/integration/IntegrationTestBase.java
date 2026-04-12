package ai.javaclaw.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared base class for integration tests.
 *
 * <p>Boots the full application context using the {@code contracttest} profile which
 * configures Testcontainers PostgreSQL via the TC JDBC driver, a dummy LLM endpoint,
 * and Flyway migrations.
 *
 * <p>When {@code -Dtest.dialect=sqlite} is set, the datasource switches to a
 * temporary SQLite file and the {@code sqlite} Spring profile is activated via
 * {@link IntegrationTestProfileResolver}. This ensures every integration test
 * runs on both database dialects.
 *
 * <p>All MockMvc requests default to an authenticated admin user (ROLE_ADMIN)
 * via {@link TestSecurityConfig}. This ensures {@code @Nested} test classes
 * work without explicit auth annotations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles(resolver = IntegrationTestProfileResolver.class)
@Import(TestSecurityConfig.class)
abstract class IntegrationTestBase {

    static final boolean USE_SQLITE = "sqlite".equals(System.getProperty("test.dialect"));

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        if (USE_SQLITE) {
            String dbPath = System.getProperty("java.io.tmpdir") + "/javaclaw-it-"
                    + ProcessHandle.current().pid() + ".db";
            registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbPath);
            registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
            registry.add("javaclaw.persistence.dialect", () -> "sqlite");
            registry.add("javaclaw.security.cookie.secure", () -> "false");
        }
        // Postgres: application-contracttest.yaml already configures TC JDBC driver
    }

    @Autowired
    MockMvc mockMvc;
}
