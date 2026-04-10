package ai.javaclaw.api.chat.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for {@link PgNotificationTransport} — T56 gap closure.
 *
 * <p>Verifies real PostgreSQL NOTIFY/LISTEN behaviour across two independent transport instances
 * (simulating two pods) sharing the same Testcontainers database.
 *
 * <ul>
 *   <li>t56a — cross-instance broadcast: podA publishes, podB receives
 *   <li>t56b — filter by conversationId: unsubscribed channel is not delivered
 *   <li>t56c — order preserved: 5 messages arrive in broadcast order
 * </ul>
 */
@Testcontainers
class PgNotificationTransportIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    private HikariDataSource dsA;
    private HikariDataSource dsB;
    private PgNotificationTransport podA;
    private PgNotificationTransport podB;

    @BeforeEach
    void setup() throws Exception {
        dsA = createDataSource();
        dsB = createDataSource();
        podA = new PgNotificationTransport(dsA);
        podB = new PgNotificationTransport(dsB);
        podA.afterPropertiesSet();
        podB.afterPropertiesSet();
        // Give listener threads time to establish LISTEN connections
        Thread.sleep(300);
    }

    @AfterEach
    void teardown() {
        if (podA != null) {
            podA.destroy();
        }
        if (podB != null) {
            podB.destroy();
        }
        if (dsA != null) {
            dsA.close();
        }
        if (dsB != null) {
            dsB.close();
        }
    }

    /**
     * T56a: cross-instance broadcast — podA.broadcast() is received by podB.subscribe().
     *
     * <p>Verifies that PG NOTIFY on one connection is delivered via LISTEN on a separate connection
     * (different HikariDataSource / simulated pod).
     */
    @Test
    void t56a_crossInstanceBroadcast() {
        List<String> received = new CopyOnWriteArrayList<>();
        podB.subscribe("conv-a").subscribe(received::add);

        podA.broadcast("conv-a", "{\"status\":\"done\"}");

        await().atMost(Duration.ofSeconds(5)).until(() -> received.size() == 1);

        assertThat(received).containsExactly("{\"status\":\"done\"}");
    }

    /**
     * T56b: filter by conversationId — podB subscribed to "conv-2" does NOT receive events for
     * "conv-1".
     *
     * <p>Verifies that the conversationId routing correctly suppresses unrelated notifications.
     */
    @Test
    void t56b_filterByConversationId() throws InterruptedException {
        List<String> receivedForConv2 = new CopyOnWriteArrayList<>();
        podB.subscribe("conv-2").subscribe(receivedForConv2::add);

        // Broadcast only to conv-1 — podB is NOT subscribed to conv-1
        podA.broadcast("conv-1", "event-for-conv-1");

        // Wait enough time to ensure the notification was processed (if it were delivered)
        Thread.sleep(2000);

        assertThat(receivedForConv2).isEmpty();
    }

    /**
     * T56c: order preserved — 5 consecutive broadcasts arrive at podB in the same order.
     *
     * <p>Verifies that PostgreSQL NOTIFY delivery and sink emission maintain FIFO ordering.
     */
    @Test
    void t56c_orderPreserved() {
        List<String> received = new CopyOnWriteArrayList<>();
        podB.subscribe("conv-c").subscribe(received::add);

        List<String> expected = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String msg = "msg-" + i;
            expected.add(msg);
            podA.broadcast("conv-c", msg);
        }

        await().atMost(Duration.ofSeconds(5)).until(() -> received.size() == 5);

        assertThat(received).containsExactlyElementsOf(expected);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(5000);
        return new HikariDataSource(config);
    }
}
