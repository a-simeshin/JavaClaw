package ai.javaclaw.api.chat.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * Unit tests for {@link PgNotificationTransport}.
 * Tests broadcast SQL generation, payload escaping, subscribe routing, and notification handling
 * without requiring a real PostgreSQL instance.
 */
class PgNotificationTransportTest {

    private DataSource dataSource;
    private Connection mockConnection;
    private Statement mockStatement;
    private PgNotificationTransport transport;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = mock(DataSource.class);
        mockConnection = mock(Connection.class);
        mockStatement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);

        transport = new PgNotificationTransport(dataSource);
    }

    /** T44: PgNotifyTransport.broadcast() issues NOTIFY command. */
    @Test
    void broadcastIssuesNotifyCommand() throws SQLException {
        transport.broadcast("conv-1", "{\"status\":\"completed\"}");

        verify(mockStatement)
                .execute("NOTIFY " + PgNotificationTransport.PG_CHANNEL + ", 'conv-1\n{\"status\":\"completed\"}'");
        verify(mockConnection).close();
    }

    @Test
    void broadcastEscapesSingleQuotesInPayload() throws SQLException {
        transport.broadcast("conv-1", "{\"msg\":\"it's done\"}");

        verify(mockStatement)
                .execute("NOTIFY " + PgNotificationTransport.PG_CHANNEL + ", 'conv-1\n{\"msg\":\"it''s done\"}'");
    }

    @Test
    void broadcastSqlExceptionDoesNotPropagate() throws SQLException {
        when(mockStatement.execute(anyString())).thenThrow(new SQLException("connection lost"));

        // Should not throw
        transport.broadcast("conv-1", "payload");
    }

    /** T45: PgNotifyTransport.subscribe() receives NOTIFY events routed by conversationId. */
    @Test
    void subscribeReceivesNotificationsForMatchingConversation() {
        Flux<String> flux = transport.subscribe("conv-1");

        List<String> received = new ArrayList<>();
        flux.subscribe(received::add);

        // Simulate receiving a PG notification (parsed from NOTIFY parameter)
        transport.handleNotification("conv-1\n{\"status\":\"done\"}");

        assertThat(received).containsExactly("{\"status\":\"done\"}");
    }

    @Test
    void subscribeDoesNotReceiveEventsForOtherConversations() {
        Flux<String> flux1 = transport.subscribe("conv-1");
        Flux<String> flux2 = transport.subscribe("conv-2");

        List<String> received1 = new ArrayList<>();
        List<String> received2 = new ArrayList<>();
        flux1.subscribe(received1::add);
        flux2.subscribe(received2::add);

        transport.handleNotification("conv-1\nmsg-for-1");
        transport.handleNotification("conv-2\nmsg-for-2");

        assertThat(received1).containsExactly("msg-for-1");
        assertThat(received2).containsExactly("msg-for-2");
    }

    @Test
    void handleNotificationWithNoSubscriberDoesNotFail() {
        // No subscriber for conv-99 — should not throw
        transport.handleNotification("conv-99\nignored");
    }

    @Test
    void handleMalformedNotificationIgnored() {
        // No newline separator — should log warning but not throw
        transport.handleNotification("malformed-payload-no-separator");
    }

    @Test
    void escapePayloadFormatAndEscaping() {
        String result = PgNotificationTransport.escapePayload("conv-1", "it's a test");
        assertThat(result).isEqualTo("conv-1\nit''s a test");
    }

    @Test
    void multipleEventsDeliveredInOrder() {
        Flux<String> flux = transport.subscribe("conv-1");

        List<String> received = new ArrayList<>();
        flux.subscribe(received::add);

        transport.handleNotification("conv-1\nfirst");
        transport.handleNotification("conv-1\nsecond");
        transport.handleNotification("conv-1\nthird");

        assertThat(received).containsExactly("first", "second", "third");
    }

    @Test
    void subscribeReturnsSameSinkForSameConversation() {
        Flux<String> flux1 = transport.subscribe("conv-1");
        Flux<String> flux2 = transport.subscribe("conv-1");

        List<String> received1 = new ArrayList<>();
        List<String> received2 = new ArrayList<>();
        flux1.subscribe(received1::add);
        flux2.subscribe(received2::add);

        transport.handleNotification("conv-1\nhello");

        // Both subscriptions receive from the same multicast sink
        assertThat(received1).containsExactly("hello");
        assertThat(received2).containsExactly("hello");
    }
}
