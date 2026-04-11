package ai.javaclaw.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Manages MCP server visibility and role-based allowlists (15.4.1-15.4.3).
 * <p>PUBLIC servers are visible to all roles.
 * RESTRICTED servers are visible only to allowlisted roles (ADMIN always sees everything).
 * Personal servers (ownerId != null) are visible only to their owner.
 */
@Service
public class McpServerVisibilityService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final McpServerRepository serverRepository;
    private final McpRoleAllowlistRepository allowlistRepository;

    public McpServerVisibilityService(
            final McpServerRepository serverRepository, final McpRoleAllowlistRepository allowlistRepository) {
        this.serverRepository = serverRepository;
        this.allowlistRepository = allowlistRepository;
    }

    /** Returns all global servers visible to the given role plus the user's personal servers. */
    public List<McpServer> listVisibleServers(final String role, final String userId) {
        final List<McpServer> result = new ArrayList<>();

        // Global servers filtered by visibility
        final List<McpServer> globalServers = serverRepository.findAllByOwnerIdIsNull();
        if (ADMIN_ROLE.equalsIgnoreCase(role)) {
            result.addAll(globalServers);
        } else {
            globalServers.stream()
                    .filter(server -> isVisibleToRole(server, role))
                    .forEach(result::add);
        }

        // Personal servers for this user
        if (userId != null) {
            result.addAll(serverRepository.findAllByOwnerId(userId));
        }

        return result;
    }

    /** Returns all enabled servers visible to the given role plus user's personal enabled servers. */
    public List<McpServer> listVisibleEnabledServers(final String role, final String userId) {
        return listVisibleServers(role, userId).stream()
                .filter(McpServer::enabled)
                .toList();
    }

    /** Sets server visibility to PUBLIC or RESTRICTED. Only for global servers. */
    public McpServer setVisibility(final String serverId, final String visibility) {
        final McpServer server = serverRepository
                .findByIdAndOwnerIdIsNull(serverId)
                .orElseThrow(() -> new NoSuchElementException("mcp server not found: " + serverId));
        if (!McpServer.VISIBILITY_PUBLIC.equals(visibility) && !McpServer.VISIBILITY_RESTRICTED.equals(visibility)) {
            throw new IllegalArgumentException("visibility must be PUBLIC or RESTRICTED");
        }
        final McpServer updated = server.withVisibility(visibility);
        return serverRepository.save(updated);
    }

    /** Gets the set of roles allowed for a RESTRICTED server. */
    public Set<String> getAllowedRoles(final String serverId) {
        return allowlistRepository.findByServerId(serverId).stream()
                .map(McpRoleAllowlist::role)
                .collect(Collectors.toSet());
    }

    /** Sets the complete allowlist for a server (replaces existing). */
    public void setAllowedRoles(final String serverId, final Set<String> roles) {
        if (!serverRepository.existsById(serverId)) {
            throw new NoSuchElementException("mcp server not found: " + serverId);
        }
        allowlistRepository.deleteAllByServerId(serverId);
        roles.forEach(role -> allowlistRepository.save(McpRoleAllowlist.create(serverId, role)));
    }

    /** Adds a single role to the server's allowlist. */
    public void addAllowedRole(final String serverId, final String role) {
        if (!serverRepository.existsById(serverId)) {
            throw new NoSuchElementException("mcp server not found: " + serverId);
        }
        if (!allowlistRepository.existsByServerIdAndRole(serverId, role)) {
            allowlistRepository.save(McpRoleAllowlist.create(serverId, role));
        }
    }

    /** Removes a single role from the server's allowlist. */
    public void removeAllowedRole(final String serverId, final String role) {
        allowlistRepository.deleteByServerIdAndRole(serverId, role);
    }

    /** Creates a personal MCP server for a specific user. */
    public McpServer createPersonalServer(
            final String userId,
            final String name,
            final String transport,
            final String command,
            final String url,
            final java.util.Map<String, String> headers,
            final boolean enabled) {
        return serverRepository.save(McpServer.newPersonal(userId, name, transport, command, url, headers, enabled));
    }

    /** Lists personal servers for a specific user. */
    public List<McpServer> listPersonalServers(final String userId) {
        return serverRepository.findAllByOwnerId(userId);
    }

    /** Deletes a personal server (only if owned by the user). */
    public void deletePersonalServer(final String serverId, final String userId) {
        final McpServer server = serverRepository
                .findByIdAndOwnerId(serverId, userId)
                .orElseThrow(() -> new NoSuchElementException("personal mcp server not found: " + serverId));
        serverRepository.deleteById(server.id());
    }

    private boolean isVisibleToRole(final McpServer server, final String role) {
        if (server.isPublic()) {
            return true;
        }
        return allowlistRepository.existsByServerIdAndRole(server.id(), role);
    }
}
