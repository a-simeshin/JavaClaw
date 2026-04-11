package ai.javaclaw.security.web;

import java.time.Instant;

public record SessionInfoDto(
        String id, Instant createdAt, Instant lastUsedAt, Instant expiresAt, String remoteAddr, String userAgent) {}
