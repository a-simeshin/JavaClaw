package ai.javaclaw.security.session;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;

/** SPI for issuing and resolving opaque session tokens. */
public interface SessionTokenService {
    /** Issue a new session token for authenticated user. */
    IssuedToken issue(Authentication authentication, ClientInfo clientInfo, Duration ttl);

    /** Resolve raw cookie value to Authentication. Returns empty if invalid/expired/revoked. */
    Optional<Authentication> resolve(String rawToken);

    /** Revoke session by raw token. No-op if not found. */
    void revoke(String rawToken, String reason);

    /** Revoke all active sessions for user. Returns count revoked. */
    int revokeAllForUser(String userId, String reason);

    /** List active sessions for user. */
    List<UserSession> listActiveSessions(String userId);

    /** Revoke session by session ID (not raw token). No-op if not found. */
    void revokeById(String sessionId, String reason);
}
