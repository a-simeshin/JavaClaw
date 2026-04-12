package ai.javaclaw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integration test for JobRunr running against the SQLite runtime profile.
 * Validates that JobRunr's auto-configuration picks up
 * {@code org.jobrunr.storage.sql.sqlite.SqLiteStorageProvider} from the application
 * DataSource URL and that a recurring job scheduled via {@link JobScheduler}
 * actually executes on top of that storage — with zero custom bean wiring.
 *
 * <p>Acceptance gate for {@code specs/multi-dialect-persistence-sqlite-standalone.md}
 * Task #18 (write-tests) — the {@code JobRunrSqliteIntegrationTest} row. Also
 * exercises the recurring-task path described by Task #13 (channel-routing).
 *
 * <p>Operational notes:
 *
 * <ul>
 *   <li>Runs under the {@code sqlite} profile with a dedicated temp file per test
 *       class — the file is deleted in {@link #cleanSqliteFileBefore()} so Flyway
 *       always starts from a pristine schema and JobRunr always creates its own
 *       tables from scratch.</li>
 *   <li>The dashboard is disabled (fixed port 8081 would otherwise collide across
 *       surefire forks) but the background-job server is <b>enabled</b> so enqueued
 *       jobs are actually picked up and executed.</li>
 *   <li>Awaitility waits up to 30 s for the worker to complete the enqueued job —
 *       enough slack for a cold JobRunr poll loop on a loaded CI box without being
 *       annoyingly slow locally.</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "jobrunr.dashboard.enabled=false",
            "jobrunr.background-job-server.enabled=true",
            // Fast polling loop so the test finishes quickly (default is 15 s).
            "jobrunr.background-job-server.poll-interval-in-seconds=5",
            // Single worker + single poller per pool — keeps the number of
            // concurrent SQLite writers small, reducing SQLITE_BUSY retries.
            "jobrunr.background-job-server.worker-count=1",
        })
@ActiveProfiles("sqlite")
class JobRunrSqliteIntegrationTest {

    private static final Path SQLITE_DB =
            Paths.get(System.getProperty("java.io.tmpdir"), "javaclaw-jobrunr-it-sqlite.db");

    @DynamicPropertySource
    static void registerSqliteFile(DynamicPropertyRegistry registry) {
        // Override the full JDBC URL (rather than just SQLITE_FILE) so we can append
        // `busy_timeout=30000` to make the sqlite driver transparently wait up to 30 s
        // for a lock instead of immediately failing with SQLITE_BUSY. JobRunr has a
        // multi-pool background architecture (JobSteward + JobServerPoller +
        // ZookeeperPool) that hammers the storage concurrently, and SQLite's
        // database-wide write lock otherwise blows up the executor within seconds.
        registry.add(
                "spring.datasource.url", () -> "jdbc:sqlite:" + SQLITE_DB + "?busy_timeout=30000&journal_mode=WAL");
    }

    @BeforeAll
    static void cleanSqliteFileBefore() throws Exception {
        deleteDb();
        if (SQLITE_DB.getParent() != null) {
            Files.createDirectories(SQLITE_DB.getParent());
        }
    }

    @AfterAll
    static void cleanSqliteFileAfter() throws Exception {
        deleteDb();
    }

    private static void deleteDb() throws Exception {
        Files.deleteIfExists(SQLITE_DB);
        Files.deleteIfExists(Paths.get(SQLITE_DB + "-journal"));
        Files.deleteIfExists(Paths.get(SQLITE_DB + "-wal"));
        Files.deleteIfExists(Paths.get(SQLITE_DB + "-shm"));
    }

    @Autowired
    private StorageProvider storageProvider;

    @Autowired
    private JobScheduler jobScheduler;

    @Autowired
    private TestJobService testJobService;

    /**
     * Asserts that JobRunr's auto-configuration resolved a SQLite-backed
     * {@link StorageProvider} from the DataSource URL rather than falling back to
     * {@code InMemoryStorageProvider}. The class name comparison is used (rather
     * than an {@code instanceof} check against
     * {@code org.jobrunr.storage.sql.sqlite.SqLiteStorageProvider}) because that
     * class is an internal part of the JobRunr SQL subsystem and is not re-exported
     * through a stable public parent type distinct from
     * {@link org.jobrunr.storage.sql.SqlStorageProvider}.
     */
    @Test
    void storageProviderIsSqliteBacked() {
        assertThat(storageProvider)
                .as("JobRunr must have auto-selected a SQL-backed StorageProvider from the sqlite DataSource")
                .isInstanceOf(org.jobrunr.storage.sql.SqlStorageProvider.class);

        assertThat(storageProvider.getClass().getName())
                .as("concrete StorageProvider implementation must be the SQLite one")
                .contains("SqLite");
    }

    /**
     * End-to-end background-job execution test: enqueue a lambda that bumps a
     * counter on {@link TestJobService} and wait until the worker actually runs it.
     * Validates that JobRunr can persist a job into the SQLite JobRunr tables,
     * poll it from storage, execute it, and mark it complete — all without any
     * custom storage wiring.
     */
    @Test
    void enqueuedJobExecutesOnSqliteStorage() {
        String marker = "sqlite-job-" + UUID.randomUUID();
        jobScheduler.<TestJobService>enqueue(svc -> svc.recordRun(marker));

        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(testJobService.lastMarker())
                        .as("TestJobService must have been invoked by the JobRunr worker")
                        .isEqualTo(marker));

        assertThat(testJobService.runCount())
                .as("TestJobService.recordRun should have been invoked at least once")
                .isGreaterThanOrEqualTo(1);
    }

    /**
     * Minimal Spring-managed bean that captures the effect of a JobRunr-scheduled
     * call. Declared as a {@link Component} inside a {@link TestConfiguration}
     * {@link Bean} method so the enclosing application context picks it up without
     * interfering with the production component scan.
     */
    public static class TestJobService {
        private final AtomicInteger counter = new AtomicInteger();
        private volatile String lastMarker;

        public void recordRun(String marker) {
            this.lastMarker = marker;
            this.counter.incrementAndGet();
        }

        public int runCount() {
            return counter.get();
        }

        public String lastMarker() {
            return lastMarker;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestJobServiceConfig {
        @Bean
        TestJobService testJobService() {
            return new TestJobService();
        }
    }
}
