package ai.javaclaw.security.session;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("user_session")
public record UserSession(
        @Id String id,
        String tokenHash,
        String userId,
        Instant createdAt,
        Instant expiresAt,
        Instant lastUsedAt,
        String remoteAddr,
        String userAgent,
        Instant revokedAt,
        String revocationReason) {
    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
