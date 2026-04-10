package ai.javaclaw.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ai.javaclaw.JavaClawApplication;
import ai.javaclaw.api.chat.delivery.PgNotificationTransport;
import ai.javaclaw.conversations.Conversation;
import ai.javaclaw.conversations.ConversationRepository;
import ai.javaclaw.delivery.NotificationTransport;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Scaling integration test — E29/E30 gap closure.
 *
 * <p>Boots <strong>two independent Spring application contexts</strong> ({@code podA}, {@code
 * podB}) in the same JVM, both pointing at a single shared {@link PostgreSQLContainer}. This
 * simulates a horizontally scaled deployment where multiple JavaClaw instances share a single
 * database.
 *
 * <h2>Covered gaps</h2>
 *
 * <ul>
 *   <li><b>E29 — shared DB visibility:</b> a {@link Conversation} written via {@code
 *       podA.getBean(ConversationRepository.class)} must be observable immediately from {@code
 *       podB}'s repository bean (i.e. the two contexts really share the same PostgreSQL database,
 *       with no local in-memory state bleed).
 *   <li><b>E30 — cross-pod SSE broadcast via PG NOTIFY:</b> {@link PgNotificationTransport} is
 *       activated in both contexts (via {@code spring.datasource.url=jdbc:postgresql://...}, which
 *       triggers {@code NotificationTransportConfiguration.PostgresCondition}). A broadcast from
 *       podA's transport is delivered to a subscriber attached to podB's transport, proving that
 *       real PostgreSQL {@code NOTIFY/LISTEN} bridges the two pods.
 * </ul>
 *
 * <h2>Isolation</h2>
 *
 * This class is intentionally placed outside the {@code .../integration/} package and tagged with
 * {@code @Tag("scaling-e2e")}, so the default Failsafe include pattern ({@code
 * **\/integration/**\/*IntegrationTest.java}) does <strong>not</strong> pick it up. It only runs
 * under the opt-in Maven profile {@code scaling-e2e}, which enlarges the heap to {@code -Xmx2g}
 * (two full Spring contexts plus a Testcontainers PostgreSQL are heavy) and filters by the
 * JUnit tag.
 *
 * <p>Skipped automatically when {@code CI_NO_DOCKER=true} (environments without Docker).
 */
@Tag("scaling-e2e")
@Testcontainers
@DisabledIfEnvironmentVariable(named = "CI_NO_DOCKER", matches = "true")
class ScalingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("javaclaw_scaling")
            .withUsername("javaclaw")
            .withPassword("javaclaw");

    private static ConfigurableApplicationContext podA;
    private static ConfigurableApplicationContext podB;

    @BeforeAll
    static void startPods() {
        podA = bootPod("podA");
        podB = bootPod("podB");
    }

    @AfterAll
    static void stopPods() {
        if (podB != null) {
            podB.close();
        }
        if (podA != null) {
            podA.close();
        }
    }

    private static ConfigurableApplicationContext bootPod(String name) {
        Map<String, Object> props = new HashMap<>();
        // Direct jdbc:postgresql URL — NOT jdbc:tc:postgresql — so that
        // NotificationTransportConfiguration.PostgresCondition activates
        // PgNotificationTransport instead of the in-memory fallback.
        props.put("spring.datasource.url", postgres.getJdbcUrl());
        props.put("spring.datasource.username", postgres.getUsername());
        props.put("spring.datasource.password", postgres.getPassword());
        props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        // Reuse the existing `it` profile for sane dev-agnostic defaults
        // (Flyway on, no LLM, JobRunr dashboard off, etc.).
        props.put("spring.profiles.active", "it");
        // Random free port per context.
        props.put("server.port", "0");
        // Distinguish logs.
        props.put("spring.application.name", "javaclaw-" + name);

        return new SpringApplicationBuilder(JavaClawApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(props)
                .run();
    }

    // ── E29 ──────────────────────────────────────────────────────────────────

    /**
     * E29 — two pods share a single PostgreSQL database.
     *
     * <p>Writes a {@link Conversation} through podA's repository bean and immediately reads it
     * back through podB's repository bean. The read succeeds only if both pods are truly
     * pointed at the same database (not an in-memory or per-pod instance).
     */
    @Test
    void e29_twoPodsSharedDb_conversationVisibleFromBoth() {
        ConversationRepository repoA = podA.getBean(ConversationRepository.class);
        ConversationRepository repoB = podB.getBean(ConversationRepository.class);

        String id = "web-" + UUID.randomUUID();
        repoA.save(Conversation.newWithId(id));

        // podB must see the row inserted by podA — same underlying PG database.
        assertThat(repoB.existsById(id))
                .as("Conversation inserted via podA must be visible from podB (shared DB)")
                .isTrue();
        assertThat(repoB.findById(id))
                .as("podB should retrieve the same Conversation instance")
                .isPresent();
    }

    // ── E30 ──────────────────────────────────────────────────────────────────

    /**
     * E30 — SSE broadcast crosses pod boundaries via PostgreSQL {@code NOTIFY/LISTEN}.
     *
     * <p>Subscribes on podB and broadcasts from podA. The event must arrive on podB within a few
     * seconds, proving that {@link PgNotificationTransport} bridges the two Spring contexts
     * through the shared database (no in-JVM shortcut — each pod has its own transport bean).
     */
    @Test
    void e30_crossPodSseViaPgNotify() {
        NotificationTransport transportA = podA.getBean(NotificationTransport.class);
        NotificationTransport transportB = podB.getBean(NotificationTransport.class);

        // Sanity check: both pods must have picked the PG-backed transport,
        // otherwise the test would accidentally pass/fail via the in-memory fallback.
        assertThat(transportA)
                .as("podA must use PgNotificationTransport (jdbc:postgresql URL)")
                .isInstanceOf(PgNotificationTransport.class);
        assertThat(transportB)
                .as("podB must use PgNotificationTransport (jdbc:postgresql URL)")
                .isInstanceOf(PgNotificationTransport.class);

        String conversationId = "conv-scaling-" + UUID.randomUUID();
        List<String> receivedOnB = new CopyOnWriteArrayList<>();
        transportB.subscribe(conversationId).subscribe(receivedOnB::add);

        // Give podB's LISTEN thread a moment to register the sink.
        sleep(300);

        transportA.broadcast(conversationId, "{\"type\":\"task.progress\",\"pct\":42}");

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(receivedOnB).hasSize(1));
        assertThat(receivedOnB).containsExactly("{\"type\":\"task.progress\",\"pct\":42}");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
