package ai.javaclaw.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.javaclaw.config.JdbcConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJdbcTest
@Testcontainers
@ActiveProfiles("test")
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(JdbcConfig.class)
class McpServerRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    McpServerRepository repository;

    // ── save / findById roundtrip ─────────────────────────────────────────────

    @Test
    void save_findById_roundtrip() {
        final McpServer server =
                McpServer.newGlobal("filesystem", "stdio", "npx @mcp/filesystem /tmp", null, null, true);

        final McpServer saved = repository.save(server);

        assertThat(saved.id()).isNotNull();
        final Optional<McpServer> found = repository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("filesystem");
        assertThat(found.get().transport()).isEqualTo("stdio");
        assertThat(found.get().command()).isEqualTo("npx @mcp/filesystem /tmp");
        assertThat(found.get().enabled()).isTrue();
        assertThat(found.get().ownerId()).isNull();
        assertThat(found.get().createdAt()).isNotNull();
        assertThat(found.get().updatedAt()).isNotNull();
    }

    // ── JSONB headers roundtrip ───────────────────────────────────────────────

    @Test
    void save_withJsonbHeaders_roundtrip() {
        final Map<String, String> headers = Map.of("Authorization", "Bearer secret", "X-Custom", "value");
        final McpServer server =
                McpServer.newGlobal("remote-search", "http", null, "https://mcp.example.com", headers, false);

        final McpServer saved = repository.save(server);
        final McpServer found = repository.findById(saved.id()).orElseThrow();

        assertThat(found.headers()).containsEntry("Authorization", "Bearer secret");
        assertThat(found.headers()).containsEntry("X-Custom", "value");
    }

    // ── findAllByOwnerIdIsNull ────────────────────────────────────────────────

    @Test
    void findAllByOwnerIdIsNull_returnsOnlyGlobal() {
        repository.save(McpServer.newGlobal("server-a", "stdio", "cmd-a", null, null, true));
        repository.save(McpServer.newGlobal("server-b", "http", null, "https://b.com", null, false));

        final List<McpServer> globals = repository.findAllByOwnerIdIsNull();

        assertThat(globals).hasSizeGreaterThanOrEqualTo(2);
        assertThat(globals).allMatch(s -> s.ownerId() == null);
        assertThat(globals).extracting(McpServer::name).contains("server-a", "server-b");
    }

    // ── partial unique index: global name enforced ────────────────────────────

    @Test
    void partialUniqueIndex_globalName_enforced() {
        repository.save(McpServer.newGlobal("unique-server", "stdio", "cmd", null, null, true));

        assertThatThrownBy(() -> repository.save(
                        McpServer.newGlobal("unique-server", "http", null, "https://x.com", null, false)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // ── deleteById ───────────────────────────────────────────────────────────

    @Test
    void deleteById_removes() {
        final McpServer saved = repository.save(McpServer.newGlobal("to-remove", "stdio", "cmd", null, null, false));
        assertThat(repository.findById(saved.id())).isPresent();

        repository.deleteById(saved.id());

        assertThat(repository.findById(saved.id())).isEmpty();
    }
}
