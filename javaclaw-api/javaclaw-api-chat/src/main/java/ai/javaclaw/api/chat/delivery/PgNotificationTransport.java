package ai.javaclaw.api.chat.delivery;

import ai.javaclaw.delivery.NotificationTransport;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * PostgreSQL NOTIFY/LISTEN-based {@link NotificationTransport} for multi-pod deployments.
 *
 * <p>Broadcasts notification payloads via {@code NOTIFY task_notifications} with a JSON payload
 * containing the conversationId and the actual notification data. All pods listening on the
 * same channel receive the notification and route it to local SSE subscribers.
 *
 * <p>Activated by {@link NotificationTransportConfiguration} when the datasource URL
 * starts with {@code jdbc:postgresql}.
 */
public class PgNotificationTransport implements NotificationTransport, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(PgNotificationTransport.class);

    static final String PG_CHANNEL = "task_notifications";
    private static final long POLL_INTERVAL_MS = 500;

    private final DataSource dataSource;
    private final Map<String, Sinks.Many<String>> subscriptions = new ConcurrentHashMap<>();
    private final ExecutorService listenerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pg-notify-listener");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running;
    private volatile Connection listenConnection;

    public PgNotificationTransport(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() {
        running = true;
        listenerExecutor.submit(this::listenLoop);
        log.info("PgNotificationTransport started — listening on channel '{}'", PG_CHANNEL);
    }

    @Override
    public void destroy() {
        running = false;
        listenerExecutor.shutdownNow();
        closeQuietly(listenConnection);
        log.info("PgNotificationTransport stopped");
    }

    @Override
    public void broadcast(String conversationId, String payload) {
        String escaped = escapePayload(conversationId, payload);
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("NOTIFY " + PG_CHANNEL + ", '" + escaped + "'");
            log.debug("PG NOTIFY sent for conversation {}", conversationId);
        } catch (SQLException e) {
            log.error("Failed to send PG NOTIFY for conversation {}", conversationId, e);
        }
    }

    @Override
    public Flux<String> subscribe(String conversationId) {
        Sinks.Many<String> sink = subscriptions.computeIfAbsent(
                conversationId, k -> Sinks.many().multicast().onBackpressureBuffer(256));
        return sink.asFlux();
    }

    /**
     * Background loop: maintains a dedicated LISTEN connection and polls for notifications.
     */
    private void listenLoop() {
        while (running) {
            try {
                listenConnection = dataSource.getConnection();
                try (Statement stmt = listenConnection.createStatement()) {
                    stmt.execute("LISTEN " + PG_CHANNEL);
                }
                log.debug("LISTEN {} established", PG_CHANNEL);

                PGConnection pgConn = listenConnection.unwrap(PGConnection.class);

                while (running) {
                    PGNotification[] notifications = pgConn.getNotifications((int) POLL_INTERVAL_MS);
                    if (notifications != null) {
                        for (PGNotification notification : notifications) {
                            handleNotification(notification.getParameter());
                        }
                    }
                }
            } catch (SQLException e) {
                if (running) {
                    log.warn("PG LISTEN connection lost, reconnecting in 1s", e);
                    sleep(1000);
                }
            } finally {
                closeQuietly(listenConnection);
                listenConnection = null;
            }
        }
    }

    /**
     * Parses a notification payload and routes it to the correct conversation sink.
     * Payload format: {@code conversationId\npayload}
     */
    void handleNotification(String rawPayload) {
        int sep = rawPayload.indexOf('\n');
        if (sep < 0) {
            log.warn("Malformed PG notification payload (no separator): {}", rawPayload);
            return;
        }
        String conversationId = rawPayload.substring(0, sep);
        String payload = rawPayload.substring(sep + 1);

        Sinks.Many<String> sink = subscriptions.get(conversationId);
        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(payload);
            if (result.isFailure()) {
                log.warn("Failed to emit PG notification for conversation {}: {}", conversationId, result);
            }
        }
    }

    /**
     * Escapes the conversationId + payload into a single PG NOTIFY parameter.
     * Format: {@code conversationId\npayload} with single quotes escaped.
     */
    static String escapePayload(String conversationId, String payload) {
        return (conversationId + "\n" + payload).replace("'", "''");
    }

    private static void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
                // intentionally empty
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
