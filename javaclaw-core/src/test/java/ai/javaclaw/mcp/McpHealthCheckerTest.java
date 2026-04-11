package ai.javaclaw.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpHealthCheckerTest {

    @Mock
    private McpServerRepository repository;

    @InjectMocks
    private McpHealthChecker checker;

    @Test
    void probe_disabledServer_returnsDisabled() {
        final McpServer server = testServer("s1", "http", false, "https://example.com", null);

        final McpHealthChecker.HealthResult result = checker.probe(server);

        assertThat(result.status()).isEqualTo("disabled");
        assertThat(result.detail()).contains("disabled");
    }

    @Test
    void probe_httpServerNoUrl_returnsError() {
        final McpServer server = testServer("s2", "http", true, null, null);

        final McpHealthChecker.HealthResult result = checker.probe(server);

        assertThat(result.status()).isEqualTo("error");
        assertThat(result.detail()).contains("No URL");
    }

    @Test
    void probe_httpServerBlankUrl_returnsError() {
        final McpServer server = testServer("s3", "http", true, "  ", null);

        final McpHealthChecker.HealthResult result = checker.probe(server);

        assertThat(result.status()).isEqualTo("error");
        assertThat(result.detail()).contains("No URL");
    }

    @Test
    void probe_httpServerInvalidUrl_returnsUnreachable() {
        final McpServer server = testServer("s4", "http", true, "not-a-url", null);

        final McpHealthChecker.HealthResult result = checker.probe(server);

        assertThat(result.status()).isEqualTo("unreachable");
    }

    @Test
    void probe_stdioServerNoCommand_returnsError() {
        final McpServer server = testServer("s5", "stdio", true, null, null);

        final McpHealthChecker.HealthResult result = checker.probe(server);

        assertThat(result.status()).isEqualTo("error");
        assertThat(result.detail()).contains("No command");
    }

    @Test
    void probe_stdioServerBlankCommand_returnsError() {
        final McpServer server = testServer("s6", "stdio", true, null, "  ");

        final McpHealthChecker.HealthResult result = checker.probe(server);

        assertThat(result.status()).isEqualTo("error");
        assertThat(result.detail()).contains("No command");
    }

    @Test
    void probe_unknownTransport_returnsUnknown() {
        final McpServer server = testServer("s7", "websocket", true, null, null);

        final McpHealthChecker.HealthResult result = checker.probe(server);

        assertThat(result.status()).isEqualTo("unknown");
        assertThat(result.detail()).contains("Unsupported transport");
    }

    @Test
    void checkAll_noEnabledServers_doesNothing() {
        when(repository.findAllByEnabledTrue()).thenReturn(List.of());

        checker.checkAll();

        verify(repository, never()).save(any());
    }

    @Test
    void checkAll_savesHealthResultForEachServer() {
        final McpServer s1 = testServer("s1", "http", true, null, null);
        final McpServer s2 = testServer("s2", "stdio", true, null, null);
        when(repository.findAllByEnabledTrue()).thenReturn(List.of(s1, s2));
        when(repository.save(any(McpServer.class))).thenAnswer(inv -> inv.getArgument(0));

        checker.checkAll();

        final ArgumentCaptor<McpServer> captor = ArgumentCaptor.forClass(McpServer.class);
        verify(repository, times(2)).save(captor.capture());
        final List<McpServer> saved = captor.getAllValues();
        assertThat(saved).allSatisfy(s -> {
            assertThat(s.lastHealthCheckAt()).isNotNull();
            assertThat(s.healthStatus()).isNotBlank();
        });
    }

    private static McpServer testServer(
            final String id, final String transport, final boolean enabled, final String url, final String command) {
        return new McpServer(
                id,
                null,
                "test-" + id,
                transport,
                command,
                url,
                Map.of(),
                enabled,
                McpServer.VISIBILITY_PUBLIC,
                Instant.now(),
                Instant.now(),
                "unknown",
                null,
                null);
    }
}
