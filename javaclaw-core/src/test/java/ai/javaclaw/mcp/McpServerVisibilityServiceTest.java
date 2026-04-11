package ai.javaclaw.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpServerVisibilityServiceTest {

    @Mock
    private McpServerRepository serverRepository;

    @Mock
    private McpRoleAllowlistRepository allowlistRepository;

    @InjectMocks
    private McpServerVisibilityService service;

    // ── listVisibleServers ────────────────────────────────────────────────────

    @Test
    void listVisibleServers_adminSeesAll() {
        final McpServer pub = globalServer("s1", "pub-server", McpServer.VISIBILITY_PUBLIC);
        final McpServer restricted = globalServer("s2", "restricted-server", McpServer.VISIBILITY_RESTRICTED);
        when(serverRepository.findAllByOwnerIdIsNull()).thenReturn(List.of(pub, restricted));
        when(serverRepository.findAllByOwnerId("user-1")).thenReturn(List.of());

        final List<McpServer> result = service.listVisibleServers("ADMIN", "user-1");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(McpServer::name).containsExactly("pub-server", "restricted-server");
    }

    @Test
    void listVisibleServers_userSeesPublicOnly() {
        final McpServer pub = globalServer("s1", "pub-server", McpServer.VISIBILITY_PUBLIC);
        final McpServer restricted = globalServer("s2", "restricted-server", McpServer.VISIBILITY_RESTRICTED);
        when(serverRepository.findAllByOwnerIdIsNull()).thenReturn(List.of(pub, restricted));
        when(serverRepository.findAllByOwnerId("user-1")).thenReturn(List.of());
        when(allowlistRepository.existsByServerIdAndRole("s2", "USER")).thenReturn(false);

        final List<McpServer> result = service.listVisibleServers("USER", "user-1");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("pub-server");
    }

    @Test
    void listVisibleServers_userSeesAllowlistedRestricted() {
        final McpServer restricted = globalServer("s1", "restricted-server", McpServer.VISIBILITY_RESTRICTED);
        when(serverRepository.findAllByOwnerIdIsNull()).thenReturn(List.of(restricted));
        when(serverRepository.findAllByOwnerId("user-1")).thenReturn(List.of());
        when(allowlistRepository.existsByServerIdAndRole("s1", "POWER_USER")).thenReturn(true);

        final List<McpServer> result = service.listVisibleServers("POWER_USER", "user-1");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("restricted-server");
    }

    @Test
    void listVisibleServers_includesPersonalServers() {
        when(serverRepository.findAllByOwnerIdIsNull()).thenReturn(List.of());
        final McpServer personal = personalServer("ps1", "user-1", "my-server");
        when(serverRepository.findAllByOwnerId("user-1")).thenReturn(List.of(personal));

        final List<McpServer> result = service.listVisibleServers("USER", "user-1");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("my-server");
        assertThat(result.getFirst().isPersonal()).isTrue();
    }

    @Test
    void listVisibleServers_nullUserId_noPersonalServers() {
        final McpServer pub = globalServer("s1", "pub-server", McpServer.VISIBILITY_PUBLIC);
        when(serverRepository.findAllByOwnerIdIsNull()).thenReturn(List.of(pub));

        final List<McpServer> result = service.listVisibleServers("USER", null);

        assertThat(result).hasSize(1);
        verify(serverRepository, never()).findAllByOwnerId(any());
    }

    // ── listVisibleEnabledServers ─────────────────────────────────────────────

    @Test
    void listVisibleEnabledServers_filtersDisabled() {
        final McpServer enabled = globalServer("s1", "enabled-server", McpServer.VISIBILITY_PUBLIC);
        final McpServer disabled = globalServerDisabled("s2", "disabled-server", McpServer.VISIBILITY_PUBLIC);
        when(serverRepository.findAllByOwnerIdIsNull()).thenReturn(List.of(enabled, disabled));

        final List<McpServer> result = service.listVisibleEnabledServers("ADMIN", null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("enabled-server");
    }

    // ── setVisibility ─────────────────────────────────────────────────────────

    @Test
    void setVisibility_success() {
        final McpServer server = globalServer("s1", "server", McpServer.VISIBILITY_PUBLIC);
        when(serverRepository.findByIdAndOwnerIdIsNull("s1")).thenReturn(Optional.of(server));
        when(serverRepository.save(any(McpServer.class))).thenAnswer(inv -> inv.getArgument(0));

        final McpServer result = service.setVisibility("s1", McpServer.VISIBILITY_RESTRICTED);

        assertThat(result.visibility()).isEqualTo(McpServer.VISIBILITY_RESTRICTED);
    }

    @Test
    void setVisibility_invalidValue_throws() {
        final McpServer server = globalServer("s1", "server", McpServer.VISIBILITY_PUBLIC);
        when(serverRepository.findByIdAndOwnerIdIsNull("s1")).thenReturn(Optional.of(server));

        assertThatThrownBy(() -> service.setVisibility("s1", "INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PUBLIC or RESTRICTED");
    }

    @Test
    void setVisibility_notFound_throws() {
        when(serverRepository.findByIdAndOwnerIdIsNull("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setVisibility("missing", McpServer.VISIBILITY_PUBLIC))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── allowlist CRUD ────────────────────────────────────────────────────────

    @Test
    void getAllowedRoles_returnsMappedRoles() {
        when(allowlistRepository.findByServerId("s1"))
                .thenReturn(
                        List.of(McpRoleAllowlist.create("s1", "USER"), McpRoleAllowlist.create("s1", "POWER_USER")));

        final Set<String> result = service.getAllowedRoles("s1");

        assertThat(result).containsExactlyInAnyOrder("USER", "POWER_USER");
    }

    @Test
    void setAllowedRoles_replacesExisting() {
        when(serverRepository.existsById("s1")).thenReturn(true);

        service.setAllowedRoles("s1", Set.of("USER", "POWER_USER"));

        verify(allowlistRepository).deleteAllByServerId("s1");
        verify(allowlistRepository, org.mockito.Mockito.times(2)).save(any(McpRoleAllowlist.class));
    }

    @Test
    void setAllowedRoles_notFound_throws() {
        when(serverRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> service.setAllowedRoles("missing", Set.of("USER")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void addAllowedRole_skipsDuplicate() {
        when(serverRepository.existsById("s1")).thenReturn(true);
        when(allowlistRepository.existsByServerIdAndRole("s1", "USER")).thenReturn(true);

        service.addAllowedRole("s1", "USER");

        verify(allowlistRepository, never()).save(any());
    }

    @Test
    void removeAllowedRole_delegates() {
        service.removeAllowedRole("s1", "USER");

        verify(allowlistRepository).deleteByServerIdAndRole("s1", "USER");
    }

    // ── personal servers ──────────────────────────────────────────────────────

    @Test
    void createPersonalServer_setsOwnerIdAndSaves() {
        when(serverRepository.save(any(McpServer.class))).thenAnswer(inv -> inv.getArgument(0));

        final McpServer result = service.createPersonalServer("user-1", "my-server", "stdio", "cmd", null, null, true);

        assertThat(result.ownerId()).isEqualTo("user-1");
        assertThat(result.name()).isEqualTo("my-server");
        assertThat(result.isPersonal()).isTrue();
    }

    @Test
    void listPersonalServers_delegates() {
        final McpServer personal = personalServer("ps1", "user-1", "my-server");
        when(serverRepository.findAllByOwnerId("user-1")).thenReturn(List.of(personal));

        final List<McpServer> result = service.listPersonalServers("user-1");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().ownerId()).isEqualTo("user-1");
    }

    @Test
    void deletePersonalServer_success() {
        final McpServer personal = personalServer("ps1", "user-1", "my-server");
        when(serverRepository.findByIdAndOwnerId("ps1", "user-1")).thenReturn(Optional.of(personal));

        service.deletePersonalServer("ps1", "user-1");

        verify(serverRepository).deleteById("ps1");
    }

    @Test
    void deletePersonalServer_notOwned_throws() {
        when(serverRepository.findByIdAndOwnerId("ps1", "user-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePersonalServer("ps1", "user-2"))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static McpServer globalServer(final String id, final String name, final String visibility) {
        return new McpServer(
                id,
                null,
                name,
                "stdio",
                "cmd",
                null,
                Map.of(),
                true,
                visibility,
                Instant.now(),
                Instant.now(),
                "unknown",
                null,
                null);
    }

    private static McpServer globalServerDisabled(final String id, final String name, final String visibility) {
        return new McpServer(
                id,
                null,
                name,
                "stdio",
                "cmd",
                null,
                Map.of(),
                false,
                visibility,
                Instant.now(),
                Instant.now(),
                "unknown",
                null,
                null);
    }

    private static McpServer personalServer(final String id, final String ownerId, final String name) {
        return new McpServer(
                id,
                ownerId,
                name,
                "stdio",
                "cmd",
                null,
                Map.of(),
                true,
                McpServer.VISIBILITY_PUBLIC,
                Instant.now(),
                Instant.now(),
                "unknown",
                null,
                null);
    }
}
