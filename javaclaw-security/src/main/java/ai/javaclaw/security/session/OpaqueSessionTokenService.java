package ai.javaclaw.security.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class OpaqueSessionTokenService implements SessionTokenService {

    private static final Duration LAST_USED_UPDATE_THROTTLE = Duration.ofSeconds(60);

    private final UserSessionRepository sessionRepository;
    private final UserDetailsService userDetailsService;
    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Instant> lastUsedUpdateCache = new ConcurrentHashMap<>();

    public OpaqueSessionTokenService(
            UserSessionRepository sessionRepository, UserDetailsService userDetailsService, JdbcTemplate jdbcTemplate) {
        this.sessionRepository = sessionRepository;
        this.userDetailsService = userDetailsService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public IssuedToken issue(Authentication authentication, ClientInfo clientInfo, Duration ttl) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = sha256Hex(rawToken);
        String sessionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        // Raw INSERT via JdbcTemplate — Spring Data JDBC's save() would emit an UPDATE
        // because the PK is client-generated (entity appears non-new), silently skipping
        // the row insertion.
        jdbcTemplate.update(
                "INSERT INTO user_session (id, token_hash, user_id, created_at, expires_at, last_used_at, remote_addr, user_agent, revoked_at, revocation_reason) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)",
                sessionId,
                tokenHash,
                authentication.getName(),
                Timestamp.from(now),
                Timestamp.from(expiresAt),
                Timestamp.from(now),
                clientInfo != null ? clientInfo.remoteAddr() : null,
                clientInfo != null ? clientInfo.userAgent() : null);
        log.debug("Issued session {} for user {}", sessionId, authentication.getName());
        return new IssuedToken(rawToken, sessionId, expiresAt);
    }

    @Override
    public Optional<Authentication> resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        String tokenHash = sha256Hex(rawToken);
        Instant now = Instant.now();
        Optional<UserSession> sessionOpt = sessionRepository.findActiveByTokenHash(tokenHash, now);
        if (sessionOpt.isEmpty()) return Optional.empty();

        UserSession session = sessionOpt.get();
        try {
            var userDetails = userDetailsService.loadUserByUsername(session.userId());
            throttledUpdateLastUsed(session.id(), now);
            var auth =
                    UsernamePasswordAuthenticationToken.authenticated(userDetails, null, userDetails.getAuthorities());
            return Optional.of(auth);
        } catch (UsernameNotFoundException e) {
            log.warn("Session {} references unknown user {}", session.id(), session.userId());
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void revoke(String rawToken, String reason) {
        if (rawToken == null || rawToken.isBlank()) return;
        String tokenHash = sha256Hex(rawToken);
        sessionRepository
                .findActiveByTokenHash(tokenHash, Instant.now())
                .ifPresent(s -> sessionRepository.revokeById(s.id(), Instant.now(), reason));
    }

    @Override
    @Transactional
    public int revokeAllForUser(String userId, String reason) {
        return sessionRepository.revokeAllByUserId(userId, Instant.now(), reason);
    }

    @Override
    public List<UserSession> listActiveSessions(String userId) {
        return sessionRepository.findAllActiveByUserId(userId, Instant.now());
    }

    @Override
    @Transactional
    public void revokeById(String sessionId, String reason) {
        sessionRepository.revokeById(sessionId, Instant.now(), reason);
    }

    private void throttledUpdateLastUsed(String sessionId, Instant now) {
        Instant lastUpdate = lastUsedUpdateCache.get(sessionId);
        if (lastUpdate == null || now.isAfter(lastUpdate.plus(LAST_USED_UPDATE_THROTTLE))) {
            lastUsedUpdateCache.put(sessionId, now);
            sessionRepository.updateLastUsedAt(sessionId, now);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
