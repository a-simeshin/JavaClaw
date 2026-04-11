package ai.javaclaw.agent.config;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Manages model access restrictions per role.
 * <p>
 * When a role has NO entries in the allowlist, all models are allowed (open access).
 * When a role has at least one entry, only those models are permitted.
 * </p>
 */
@Service
public class RoleModelAllowlistService {

    private final RoleModelAllowlistRepository repository;
    private final ConcurrentMap<String, List<String>> cache = new ConcurrentHashMap<>();

    public RoleModelAllowlistService(final RoleModelAllowlistRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the list of allowed model IDs for the given role.
     * Empty list means all models are allowed.
     */
    public List<String> getAllowedModels(final String role) {
        if (role == null || role.isBlank()) {
            return List.of();
        }
        return cache.computeIfAbsent(role, this::loadAllowedModels);
    }

    /**
     * Checks if the given model is allowed for the role.
     * Returns true if the allowlist is empty (open access) or if the model is in the list.
     */
    public boolean isModelAllowed(final String role, @Nullable final String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return true; // null model = system default, always allowed
        }
        final List<String> allowed = getAllowedModels(role);
        return allowed.isEmpty() || allowed.contains(modelId);
    }

    /**
     * Validates model access and throws if denied.
     */
    public void validateModelAccess(final String role, @Nullable final String modelId) {
        if (!isModelAllowed(role, modelId)) {
            throw new ModelAccessDeniedException(role, modelId);
        }
    }

    /**
     * Sets the complete allowlist for a role (replaces existing entries).
     */
    public void setAllowedModels(final String role, final List<String> modelIds) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role must not be blank");
        }
        repository.deleteAllByRole(role);
        if (modelIds != null) {
            for (final String modelId : modelIds) {
                if (modelId != null && !modelId.isBlank()) {
                    repository.save(RoleModelAllowlist.create(role, modelId));
                }
            }
        }
        evictCache(role);
    }

    /**
     * Adds a single model to the role's allowlist.
     */
    public void addAllowedModel(final String role, final String modelId) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role must not be blank");
        }
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("Model ID must not be blank");
        }
        if (!repository.existsByRoleAndModelId(role, modelId)) {
            repository.save(RoleModelAllowlist.create(role, modelId));
        }
        evictCache(role);
    }

    /**
     * Removes a single model from the role's allowlist.
     */
    public void removeAllowedModel(final String role, final String modelId) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role must not be blank");
        }
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("Model ID must not be blank");
        }
        repository.deleteByRoleAndModelId(role, modelId);
        evictCache(role);
    }

    public void evictCache(final String role) {
        cache.remove(role);
    }

    public void evictAllCache() {
        cache.clear();
    }

    private List<String> loadAllowedModels(final String role) {
        return repository.findByRole(role).stream()
                .map(RoleModelAllowlist::modelId)
                .toList();
    }

    /**
     * Exception thrown when a role attempts to use a model not in its allowlist.
     */
    public static class ModelAccessDeniedException extends RuntimeException {
        private final String role;
        private final String modelId;

        public ModelAccessDeniedException(final String role, final String modelId) {
            super("Model '" + modelId + "' is not allowed for role '" + role + "'");
            this.role = role;
            this.modelId = modelId;
        }

        public String getRole() {
            return role;
        }

        public String getModelId() {
            return modelId;
        }
    }
}
