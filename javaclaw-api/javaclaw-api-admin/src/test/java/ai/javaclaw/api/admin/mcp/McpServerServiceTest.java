package ai.javaclaw.api.admin.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.pipeline.ToolCallbackResolver;
import ai.javaclaw.mcp.McpServer;
import ai.javaclaw.mcp.McpServerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpServerServiceTest {

    @Mock
    private McpServerRepository repository;

    @Mock
    private ToolCallbackResolver toolCallbackResolver;

    @InjectMocks
    private McpServerService service;

    // ── list ─────────────────────────────────────────────────────────────────

    @Test
    void list_returnsAllGlobal_mappedToDto() {
        final McpServer s1 = server("id-1", "filesystem", "stdio", true);
        final McpServer s2 = server("id-2", "remote", "http", false);
        when(repository.findAllByOwnerIdIsNull()).thenReturn(List.of(s1, s2));

        final List<McpServerDto> result = service.list();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(McpServerDto::id).containsExactly("id-1", "id-2");
        assertThat(result).extracting(McpServerDto::name).containsExactly("filesystem", "remote");
        assertThat(result).extracting(McpServerDto::enabled).containsExactly(true, false);
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_savesWithNullOwnerId_returnsDto() {
        final McpServerDto dto = new McpServerDto(null, "filesystem", "stdio", "npx fs", null, null, true);
        final McpServer saved = server("generated-id", "filesystem", "stdio", true);
        when(repository.save(any(McpServer.class))).thenReturn(saved);

        final McpServerDto result = service.create(dto);

        assertThat(result.id()).isEqualTo("generated-id");
        assertThat(result.name()).isEqualTo("filesystem");
        assertThat(result.enabled()).isTrue();
        verify(repository).save(any(McpServer.class));
    }

    @Test
    void create_withHeaders_preservesHeaders() {
        final Map<String, String> headers = Map.of("Authorization", "Bearer token");
        final McpServerDto dto = new McpServerDto(null, "remote", "http", null, "https://example.com", headers, false);
        final McpServer saved = serverWithHeaders("gen-id", "remote", "http", false, headers);
        when(repository.save(any(McpServer.class))).thenReturn(saved);

        final McpServerDto result = service.create(dto);

        assertThat(result.headers()).containsEntry("Authorization", "Bearer token");
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_existingServer_fullReplace() {
        final McpServer current = server("id-x", "old-name", "stdio", false);
        when(repository.findByIdAndOwnerIdIsNull("id-x")).thenReturn(Optional.of(current));
        final McpServer updated = server("id-x", "new-name", "http", true);
        when(repository.save(any(McpServer.class))).thenReturn(updated);

        final McpServerDto result =
                service.update("id-x", new McpServerDto(null, "new-name", "http", null, "https://x.com", null, true));

        assertThat(result.name()).isEqualTo("new-name");
        assertThat(result.transport()).isEqualTo("http");
        assertThat(result.enabled()).isTrue();
    }

    @Test
    void update_missingServer_throwsNoSuchElementException() {
        when(repository.findByIdAndOwnerIdIsNull("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> service.update("missing", new McpServerDto(null, "x", "stdio", null, null, null, false)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("missing");
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_existingServer_callsDeleteById() {
        final McpServer existing = server("id-del", "to-delete", "stdio", true);
        when(repository.findByIdAndOwnerIdIsNull("id-del")).thenReturn(Optional.of(existing));

        service.delete("id-del");

        verify(repository).deleteById("id-del");
    }

    @Test
    void delete_missingServer_throwsNoSuchElementException() {
        when(repository.findByIdAndOwnerIdIsNull("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("gone"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("gone");
    }

    // ── status ─────────────────────────────────────────────────────────────────

    @Test
    void status_disabledServer_returnsDisabled() {
        final McpServer disabled = server("id-dis", "test-server", "http", false);
        when(repository.findByIdAndOwnerIdIsNull("id-dis")).thenReturn(Optional.of(disabled));

        final McpServerStatusDto result = service.status("id-dis");

        assertThat(result.status()).isEqualTo("disabled");
        assertThat(result.checkedAt()).isNull();
    }

    @Test
    void status_enabledServerWithHealthData_returnsRealHealth() {
        final Instant checkedAt = Instant.parse("2026-04-10T12:00:00Z");
        final McpServer healthy =
                serverWithHealth("id-ok", "http-server", "http", true, "connected", "HTTP 200", checkedAt);
        when(repository.findByIdAndOwnerIdIsNull("id-ok")).thenReturn(Optional.of(healthy));

        final McpServerStatusDto result = service.status("id-ok");

        assertThat(result.status()).isEqualTo("connected");
        assertThat(result.detail()).isEqualTo("HTTP 200");
        assertThat(result.checkedAt()).isEqualTo(checkedAt.toString());
    }

    @Test
    void status_enabledServerNoHealthCheck_returnsUnknown() {
        final McpServer unchecked = server("id-uc", "new-server", "http", true);
        when(repository.findByIdAndOwnerIdIsNull("id-uc")).thenReturn(Optional.of(unchecked));

        final McpServerStatusDto result = service.status("id-uc");

        assertThat(result.status()).isEqualTo("unknown");
        assertThat(result.checkedAt()).isNull();
    }

    // ── cache invalidation ─────────────────────────────────────────────────────

    @Test
    void create_invalidatesToolCache() {
        final McpServerDto dto = new McpServerDto(null, "test", "stdio", "cmd", null, null, true);
        when(repository.save(any(McpServer.class))).thenReturn(server("id-1", "test", "stdio", true));

        service.create(dto);

        verify(toolCallbackResolver).invalidate();
    }

    @Test
    void update_invalidatesToolCache() {
        final McpServer existing = server("id-x", "old", "stdio", false);
        when(repository.findByIdAndOwnerIdIsNull("id-x")).thenReturn(Optional.of(existing));
        when(repository.save(any(McpServer.class))).thenReturn(server("id-x", "new", "http", true));

        service.update("id-x", new McpServerDto(null, "new", "http", null, "https://x.com", null, true));

        verify(toolCallbackResolver).invalidate();
    }

    @Test
    void delete_invalidatesToolCache() {
        final McpServer existing = server("id-del", "to-delete", "stdio", true);
        when(repository.findByIdAndOwnerIdIsNull("id-del")).thenReturn(Optional.of(existing));

        service.delete("id-del");

        verify(toolCallbackResolver).invalidate();
    }

    @Test
    void toolCacheInfo_delegatesToResolver() {
        when(toolCallbackResolver.cachedToolNames()).thenReturn(List.of("tool1", "tool2"));
        when(toolCallbackResolver.cachedToolCount()).thenReturn(2);

        final ToolCacheInfoDto info = service.toolCacheInfo();

        assertThat(info.toolNames()).containsExactly("tool1", "tool2");
        assertThat(info.count()).isEqualTo(2);
    }

    @Test
    void toolCacheInfo_nullResolver_returnsEmpty() {
        // Service with null resolver (no ToolCallbackResolver bean available)
        final McpServerService serviceNoResolver = new McpServerService(repository, null);

        final ToolCacheInfoDto info = serviceNoResolver.toolCacheInfo();

        assertThat(info.toolNames()).isEmpty();
        assertThat(info.count()).isZero();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static McpServer server(final String id, final String name, final String transport, final boolean enabled) {
        return new McpServer(
                id,
                null,
                name,
                transport,
                null,
                null,
                Map.of(),
                enabled,
                Instant.now(),
                Instant.now(),
                "unknown",
                null,
                null);
    }

    private static McpServer serverWithHeaders(
            final String id,
            final String name,
            final String transport,
            final boolean enabled,
            final Map<String, String> headers) {
        return new McpServer(
                id,
                null,
                name,
                transport,
                null,
                null,
                headers,
                enabled,
                Instant.now(),
                Instant.now(),
                "unknown",
                null,
                null);
    }

    private static McpServer serverWithHealth(
            final String id,
            final String name,
            final String transport,
            final boolean enabled,
            final String healthStatus,
            final String healthDetail,
            final Instant lastCheck) {
        return new McpServer(
                id,
                null,
                name,
                transport,
                null,
                "https://example.com",
                Map.of(),
                enabled,
                Instant.now(),
                Instant.now(),
                healthStatus,
                healthDetail,
                lastCheck);
    }
}
