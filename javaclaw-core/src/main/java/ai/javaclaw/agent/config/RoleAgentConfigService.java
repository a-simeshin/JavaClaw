package ai.javaclaw.agent.config;

import ai.javaclaw.users.PermissionService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Service for managing per-role agent configurations (15.6.1).
 *
 * <p>Resolves agent config for a role with hierarchy fallback:
 * if no config exists for the exact role, walks up the role hierarchy
 * (USER → POWER_USER → ADMIN) to find the nearest ancestor config.
 * Falls back to system defaults if nothing is found.
 */
@Service
public class RoleAgentConfigService {

    private static final Logger log = LoggerFactory.getLogger(RoleAgentConfigService.class);

    private final RoleAgentConfigRepository repository;
    private final PermissionService permissionService;
    private final ConcurrentMap<String, RoleAgentConfig> cache = new ConcurrentHashMap<>();

    public RoleAgentConfigService(RoleAgentConfigRepository repository, PermissionService permissionService) {
        this.repository = repository;
        this.permissionService = permissionService;
    }

    /**
     * Resolves the agent config for a role, walking up the hierarchy if no exact match.
     * Returns empty if no config exists anywhere in the hierarchy.
     */
    public Optional<RoleAgentConfig> resolveForRole(@Nullable String role) {
        if (role == null || role.isBlank()) {
            return Optional.empty();
        }

        // Try cache first
        RoleAgentConfig cached = cache.get(role);
        if (cached != null) {
            return Optional.of(cached);
        }

        // Try exact match
        Optional<RoleAgentConfig> config = repository.findByRole(role);
        if (config.isPresent()) {
            cache.put(role, config.get());
            return config;
        }

        // Walk up hierarchy: USER → POWER_USER → ADMIN
        List<String> hierarchy = List.of("USER", "POWER_USER", "ADMIN");
        int roleIndex = hierarchy.indexOf(role);
        if (roleIndex >= 0) {
            for (int i = roleIndex + 1; i < hierarchy.size(); i++) {
                config = repository.findByRole(hierarchy.get(i));
                if (config.isPresent()) {
                    cache.put(role, config.get());
                    return config;
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Returns the model ID to use for a given role, or null if system default should be used.
     */
    @Nullable
    public String resolveModelForRole(@Nullable String role) {
        return resolveForRole(role).map(RoleAgentConfig::modelId).orElse(null);
    }

    /** Get config for a specific role (exact match only). */
    public Optional<RoleAgentConfig> getForRole(String role) {
        Assert.hasText(role, "role must not be blank");
        return repository.findByRole(role);
    }

    /** List all role agent configs. */
    public List<RoleAgentConfig> listAll() {
        return repository.findAllOrdered();
    }

    /** Create or update agent config for a role. */
    public RoleAgentConfig save(
            String role,
            @Nullable String modelId,
            boolean thinkingEnabled,
            int thinkingBudget,
            @Nullable String fallbackModels,
            int maxContextTokens) {
        Assert.hasText(role, "role must not be blank");

        Optional<RoleAgentConfig> existing = repository.findByRole(role);
        RoleAgentConfig config;
        if (existing.isPresent()) {
            config = new RoleAgentConfig(
                    existing.get().id(),
                    role,
                    modelId,
                    thinkingEnabled,
                    thinkingBudget,
                    fallbackModels,
                    maxContextTokens);
        } else {
            config = RoleAgentConfig.create(
                    role, modelId, thinkingEnabled, thinkingBudget, fallbackModels, maxContextTokens);
        }
        RoleAgentConfig saved = repository.save(config);
        cache.remove(role);
        log.info("Saved agent config for role {}: model={}", role, modelId);
        return saved;
    }

    /** Delete config for a role. */
    public void delete(String role) {
        Assert.hasText(role, "role must not be blank");
        repository.deleteByRole(role);
        cache.remove(role);
        log.info("Deleted agent config for role {}", role);
    }

    /** Evict cache for a role (e.g. after external changes). */
    public void evictCache(String role) {
        cache.remove(role);
    }

    /** Evict all cache entries. */
    public void evictAllCache() {
        cache.clear();
    }
}
