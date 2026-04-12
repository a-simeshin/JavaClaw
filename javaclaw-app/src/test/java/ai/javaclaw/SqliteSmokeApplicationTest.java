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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Smoke test for the {@code sqlite} runtime profile.
 *
 * <p>Validates that the entire Spring Boot application (web + Flyway + Spring AI JDBC
 * chat memory + JobRunr) can boot against a file-backed SQLite database with no
 * PostgreSQL infrastructure in sight. Covers Task #17 (runtime-profiles) from
 * {@code specs/multi-dialect-persistence-sqlite-standalone.md}.
 *
 * <p>Uses a dedicated temp file per JVM run — the file is deleted up front so Flyway
 * starts from a clean schema every time and the vendor migration count assertion
 * remains deterministic.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            // JobRunr dashboard binds a fixed port by default — disable here so
            // parallel surefire forks and re-runs do not collide on port 8081.
            "jobrunr.dashboard.enabled=false",
            "jobrunr.background-job-server.enabled=false"
        })
@ActiveProfiles("sqlite")
class SqliteSmokeApplicationTest {

    private static final Path SQLITE_DB = Paths.get(System.getProperty("java.io.tmpdir"), "javaclaw-smoke-sqlite.db");

    @DynamicPropertySource
    static void registerSqliteFile(DynamicPropertyRegistry registry) {
        registry.add("SQLITE_FILE", SQLITE_DB::toString);
    }

    @BeforeAll
    static void cleanSqliteFile() throws Exception {
        // Ensure a pristine DB so Flyway always runs the full migration chain
        // and the assertions below stay deterministic across local reruns.
        Files.deleteIfExists(SQLITE_DB);
        // SQLite WAL / journal side-files — defensive cleanup.
        Files.deleteIfExists(Paths.get(SQLITE_DB + "-journal"));
        Files.deleteIfExists(Paths.get(SQLITE_DB + "-wal"));
        Files.deleteIfExists(Paths.get(SQLITE_DB + "-shm"));
        if (SQLITE_DB.getParent() != null) {
            Files.createDirectories(SQLITE_DB.getParent());
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void contextLoadsAndHealthIsUp() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/actuator/health"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .as("actuator/health should return 2xx on sqlite profile")
                .isBetween(200, 299);
        assertThat(response.body()).contains("UP");
    }

    @Test
    void flywayAppliedAllSqliteMigrations() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
        assertThat(migrationCount)
                .as("full sqlite migration chain should be applied (>=40 migrations)")
                .isNotNull()
                .isGreaterThanOrEqualTo(40);
    }

    @Test
    void dialectIsSqlite() {
        // sqlite_version() exists only on SQLite — validates that SqliteJdbcConfiguration
        // is wired and the DataSource points at sqlite, not Postgres.
        String version = jdbcTemplate.queryForObject("SELECT sqlite_version()", String.class);
        assertThat(version).isNotBlank();
    }
}
