package ai.javaclaw;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Smoke test for the {@code debug} runtime profile — validates the profile group
 * resolution ({@code debug} pulls in {@code sqlite} via
 * {@code spring.profiles.group.debug}) so debug-only sessions get the SQLite dialect
 * and a tmp-file database without extra flags.
 *
 * <p>Overrides {@code SQLITE_FILE} to a dedicated per-run path so we never clobber
 * the normal sqlite profile database, and so successive runs always start clean.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            // JobRunr dashboard binds a fixed port by default — disable here so
            // parallel surefire forks and re-runs do not collide on port 8081.
            "jobrunr.dashboard.enabled=false",
            "jobrunr.background-job-server.enabled=false"
        })
@ActiveProfiles("debug")
class DebugProfileSmokeTest {

    private static final Path DEBUG_SQLITE_DB =
            Paths.get(System.getProperty("java.io.tmpdir"), "javaclaw-debug-smoke.db");

    @DynamicPropertySource
    static void registerSqliteFile(DynamicPropertyRegistry registry) {
        registry.add("SQLITE_FILE", DEBUG_SQLITE_DB::toString);
    }

    @BeforeAll
    static void cleanDebugSqliteFile() throws Exception {
        Files.deleteIfExists(DEBUG_SQLITE_DB);
        Files.deleteIfExists(Paths.get(DEBUG_SQLITE_DB + "-journal"));
        Files.deleteIfExists(Paths.get(DEBUG_SQLITE_DB + "-wal"));
        Files.deleteIfExists(Paths.get(DEBUG_SQLITE_DB + "-shm"));
        if (DEBUG_SQLITE_DB.getParent() != null) {
            Files.createDirectories(DEBUG_SQLITE_DB.getParent());
        }
    }

    @LocalServerPort
    private int port;

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void fullBootWithDebugProfile() throws Exception {
        HttpResponse<String> health = get("/actuator/health");
        assertThat(health.statusCode())
                .as("debug profile must start cleanly and expose actuator/health")
                .isBetween(200, 299);
        assertThat(health.body()).contains("UP");
    }

    @Test
    void debugProfileResolvesSqliteDatabase() {
        // SQLITE_FILE is overridden above to a tmp path — after boot the file must exist,
        // which proves the sqlite driver is active (not Postgres).
        assertThat(Files.exists(DEBUG_SQLITE_DB))
                .as("debug profile should materialize a sqlite file at SQLITE_FILE")
                .isTrue();
    }

    @Test
    void rootReturnsSpaIndex() throws Exception {
        // React SPA index.html is served by the spa-fallback controller.
        // We don't care about body contents here, only that the request does not 5xx.
        HttpResponse<String> root = get("/");
        assertThat(root.statusCode())
                .as("root / should not 5xx under debug profile")
                .isLessThan(500);
    }
}
