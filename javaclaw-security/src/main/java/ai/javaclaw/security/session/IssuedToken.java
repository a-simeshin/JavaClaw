package ai.javaclaw.security.session;

import java.time.Instant;

/** Result of SessionTokenService.issue(): raw cookie value + metadata. */
public record IssuedToken(String rawToken, String sessionId, Instant expiresAt) {}
