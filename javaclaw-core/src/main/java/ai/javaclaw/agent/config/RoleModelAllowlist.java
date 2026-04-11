package ai.javaclaw.agent.config;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Maps a role to an allowed AI model ID.
 * When no entries exist for a role, all models are allowed.
 * When at least one entry exists, only listed models are permitted.
 */
@Table("role_model_allowlist")
public record RoleModelAllowlist(@Id String id, String role, String modelId, Instant createdAt) {
    public static RoleModelAllowlist create(final String role, final String modelId) {
        return new RoleModelAllowlist(null, role, modelId, Instant.now());
    }
}
