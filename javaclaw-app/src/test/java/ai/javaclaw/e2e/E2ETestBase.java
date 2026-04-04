package ai.javaclaw.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for end-to-end browser tests using Playwright for Java.
 *
 * <p>Uses the singleton pattern for both the PostgreSQL container and the Playwright
 * browser instance. This avoids restart issues and browser reinitialization when
 * multiple test classes share the same JVM.
 *
 * <p><b>Performance design:</b> all E2E test classes share a single PostgreSQL container,
 * a single Spring application context (same {@code @SpringBootTest} + {@code @ActiveProfiles}
 * configuration ensures Spring context caching), and a single Playwright browser instance.
 * Only the lightweight {@link BrowserContext} is created per test for isolation (~10ms).
 *
 * <p>Prerequisites:
 * <ul>
 *   <li>Set the {@code OPENROUTER_API_KEY} environment variable with a valid OpenRouter API key.</li>
 *   <li>Docker must be running for the PostgreSQL Testcontainer.</li>
 * </ul>
 *
 * <p>All E2E tests are tagged with {@code "e2e"} and gated behind the {@code OPENROUTER_API_KEY}
 * environment variable so they are skipped in regular CI unless explicitly enabled.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@Tag("e2e")
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
abstract class E2ETestBase {

    // --- Singleton PostgreSQL container (shared across all E2E test classes) ---

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("javaclaw_e2e")
            .withUsername("javaclaw")
            .withPassword("javaclaw")
            .withReuse(true);

    // --- Singleton Playwright browser (shared across all E2E test classes) ---

    private static final Playwright PLAYWRIGHT;
    private static final Browser BROWSER;

    static {
        postgres.start();
        PLAYWRIGHT = Playwright.create();
        BROWSER = PLAYWRIGHT
                .chromium()
                .launch(new BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setArgs(java.util.List.of(
                                "--disable-gpu", "--disable-dev-shm-usage", "--disable-extensions", "--no-sandbox")));

        // Shut down Playwright when the JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            BROWSER.close();
            PLAYWRIGHT.close();
        }));
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbcClient;

    BrowserContext context;
    Page page;

    @BeforeEach
    void cleanDatabase() {
        // Clean chat memory so previous test messages (especially those with null content)
        // don't cause crashes when WebSocket reconnects and loads history.
        for (String table : new String[] {"spring_ai_chat_memory", "tasks"}) {
            try {
                jdbcClient.sql("DELETE FROM " + table).update();
            } catch (Exception ignored) {
                // Table may not exist — that's fine
            }
        }
    }

    @BeforeEach
    void setUpBrowserContext() {
        context = BROWSER.newContext();
        page = context.newPage();
    }

    @AfterEach
    void tearDownBrowserContext() {
        if (context != null) context.close();
    }

    /**
     * Returns the base URL for the running Spring Boot application.
     */
    String baseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * Navigates the current page to the given path (relative to the app base URL).
     */
    void navigateTo(String path) {
        page.navigate(baseUrl() + path);
    }
}
