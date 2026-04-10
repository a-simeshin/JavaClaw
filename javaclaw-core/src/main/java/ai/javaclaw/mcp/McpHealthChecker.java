package ai.javaclaw.mcp;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodically probes registered MCP servers and persists health status.
 *
 * <p>HTTP servers: HEAD request to the configured URL (5s connect + 5s read timeout).
 * Stdio servers: verifies that the command binary is non-blank (actual process liveness
 * requires a running session — only config validation is possible at probe time).
 */
@Service
public class McpHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(McpHealthChecker.class);
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    private final McpServerRepository repository;

    public McpHealthChecker(final McpServerRepository repository) {
        this.repository = repository;
    }

    /**
     * Runs every 60 seconds. Probes all enabled MCP servers and updates their health status.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void checkAll() {
        final List<McpServer> servers = repository.findAllByEnabledTrue();
        for (final McpServer server : servers) {
            try {
                final HealthResult result = probe(server);
                repository.save(server.withHealthCheck(result.status(), result.detail()));
            } catch (final Exception e) {
                log.warn("Health check failed for MCP server '{}': {}", server.name(), e.getMessage());
                repository.save(server.withHealthCheck("error", "Health check exception: " + e.getMessage()));
            }
        }
    }

    /**
     * Probes a single MCP server and returns its health result (without persisting).
     */
    public HealthResult probe(final McpServer server) {
        if (!server.enabled()) {
            return new HealthResult("disabled", "Server is disabled");
        }
        return switch (server.transport()) {
            case "http" -> probeHttp(server);
            case "stdio" -> probeStdio(server);
            default -> new HealthResult("unknown", "Unsupported transport: " + server.transport());
        };
    }

    private HealthResult probeHttp(final McpServer server) {
        final String url = server.url();
        if (url == null || url.isBlank()) {
            return new HealthResult("error", "No URL configured");
        }
        try {
            final HttpURLConnection conn =
                    (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            try {
                final int code = conn.getResponseCode();
                if (code >= 200 && code < 500) {
                    return new HealthResult("connected", "HTTP " + code);
                }
                return new HealthResult("error", "HTTP " + code);
            } finally {
                conn.disconnect();
            }
        } catch (final Exception e) {
            return new HealthResult("unreachable", e.getMessage());
        }
    }

    private HealthResult probeStdio(final McpServer server) {
        final String command = server.command();
        if (command == null || command.isBlank()) {
            return new HealthResult("error", "No command configured");
        }
        // Extract binary name from command string (first token)
        final String binary = command.split("\\s+")[0];
        try {
            final Process process = new ProcessBuilder("which", binary)
                    .redirectErrorStream(true)
                    .start();
            final boolean found = process.waitFor() == 0;
            if (found) {
                return new HealthResult("connected", "Binary '" + binary + "' found on PATH");
            }
            return new HealthResult("unreachable", "Binary '" + binary + "' not found on PATH");
        } catch (final Exception e) {
            return new HealthResult("error", "Cannot verify binary: " + e.getMessage());
        }
    }

    /** Immutable health probe result. */
    public record HealthResult(String status, String detail) {}
}
