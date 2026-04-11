package ai.javaclaw.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class OpaqueSessionTokenServiceTest {

    @Mock
    private UserSessionRepository sessionRepository;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private OpaqueSessionTokenService service;

    private Authentication mockAuth;
    private UserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        service = new OpaqueSessionTokenService(sessionRepository, userDetailsService, jdbcTemplate);

        mockUserDetails = User.withUsername("alice")
                .password("irrelevant")
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .build();

        mockAuth = UsernamePasswordAuthenticationToken.authenticated(
                mockUserDetails, null, mockUserDetails.getAuthorities());
    }

    // -----------------------------------------------------------------------
    // issue()
    // -----------------------------------------------------------------------

    @Test
    void issue_savesSessionAndReturnsNonNullToken() {
        IssuedToken token = service.issue(mockAuth, null, Duration.ofHours(1));

        assertThat(token).isNotNull();
        assertThat(token.rawToken()).isNotBlank();
        assertThat(token.sessionId()).isNotBlank();
        assertThat(token.expiresAt()).isAfter(Instant.now());

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();
        // id, tokenHash, user_id, createdAt, expiresAt, lastUsedAt, remoteAddr, userAgent
        assertThat(args).hasSize(8);
        assertThat(args[2]).isEqualTo("alice"); // user_id
        assertThat(args[1]).asString().isNotBlank(); // token_hash
    }

    @Test
    void issue_rawTokenIs43CharsBase64Url() {
        IssuedToken token = service.issue(mockAuth, null, Duration.ofHours(1));

        // 32 bytes base64url without padding = 43 chars
        assertThat(token.rawToken()).hasSize(43);
        assertThat(token.rawToken()).matches("[A-Za-z0-9_\\-]+");
    }

    @Test
    void issue_storesClientInfo() {
        ClientInfo clientInfo = new ClientInfo("127.0.0.1", "TestAgent/1.0");

        service.issue(mockAuth, clientInfo, Duration.ofHours(1));

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();
        assertThat(args[6]).isEqualTo("127.0.0.1"); // remoteAddr
        assertThat(args[7]).isEqualTo("TestAgent/1.0"); // userAgent
    }

    // -----------------------------------------------------------------------
    // resolve()
    // -----------------------------------------------------------------------

    @Test
    void resolve_validToken_returnsAuthentication() {
        IssuedToken issued = service.issue(mockAuth, null, Duration.ofHours(1));

        UserSession activeSession = new UserSession(
                issued.sessionId(),
                hashOf(issued.rawToken()),
                "alice",
                Instant.now(),
                issued.expiresAt(),
                Instant.now(),
                null,
                null,
                null,
                null);
        when(sessionRepository.findActiveByTokenHash(eq(hashOf(issued.rawToken())), any(Instant.class)))
                .thenReturn(Optional.of(activeSession));
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(mockUserDetails);

        Optional<Authentication> result = service.resolve(issued.rawToken());

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("alice");
    }

    @Test
    void resolve_wrongToken_returnsEmpty() {
        when(sessionRepository.findActiveByTokenHash(anyString(), any(Instant.class)))
                .thenReturn(Optional.empty());

        Optional<Authentication> result = service.resolve("totally-wrong-token");

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_nullToken_returnsEmpty() {
        Optional<Authentication> result = service.resolve(null);

        assertThat(result).isEmpty();
        verifyNoInteractions(sessionRepository);
    }

    @Test
    void resolve_blankToken_returnsEmpty() {
        Optional<Authentication> result = service.resolve("   ");

        assertThat(result).isEmpty();
        verifyNoInteractions(sessionRepository);
    }

    @Test
    void resolve_expiredSession_returnsEmpty() {
        // Repository returns empty because query filters by expires_at > now
        when(sessionRepository.findActiveByTokenHash(anyString(), any(Instant.class)))
                .thenReturn(Optional.empty());

        Optional<Authentication> result = service.resolve("some-expired-raw-token");

        assertThat(result).isEmpty();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void resolve_unknownUser_returnsEmpty() {
        IssuedToken issued = service.issue(mockAuth, null, Duration.ofHours(1));

        UserSession activeSession = new UserSession(
                issued.sessionId(),
                hashOf(issued.rawToken()),
                "ghost",
                Instant.now(),
                issued.expiresAt(),
                Instant.now(),
                null,
                null,
                null,
                null);
        when(sessionRepository.findActiveByTokenHash(eq(hashOf(issued.rawToken())), any(Instant.class)))
                .thenReturn(Optional.of(activeSession));
        when(userDetailsService.loadUserByUsername("ghost"))
                .thenThrow(new UsernameNotFoundException("ghost not found"));

        Optional<Authentication> result = service.resolve(issued.rawToken());

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // revoke()
    // -----------------------------------------------------------------------

    @Test
    void revoke_validToken_revokesSession() {
        IssuedToken issued = service.issue(mockAuth, null, Duration.ofHours(1));

        UserSession activeSession = new UserSession(
                issued.sessionId(),
                hashOf(issued.rawToken()),
                "alice",
                Instant.now(),
                issued.expiresAt(),
                Instant.now(),
                null,
                null,
                null,
                null);
        when(sessionRepository.findActiveByTokenHash(eq(hashOf(issued.rawToken())), any(Instant.class)))
                .thenReturn(Optional.of(activeSession));

        service.revoke(issued.rawToken(), "logout");

        verify(sessionRepository).revokeById(eq(issued.sessionId()), any(Instant.class), eq("logout"));
    }

    @Test
    void revoke_nullToken_isNoOp() {
        service.revoke(null, "reason");
        verifyNoInteractions(sessionRepository);
    }

    @Test
    void revoke_notFoundToken_isNoOp() {
        when(sessionRepository.findActiveByTokenHash(anyString(), any(Instant.class)))
                .thenReturn(Optional.empty());

        service.revoke("unknown-token", "reason");

        verify(sessionRepository, never()).revokeById(anyString(), any(), anyString());
    }

    // -----------------------------------------------------------------------
    // revokeAllForUser()
    // -----------------------------------------------------------------------

    @Test
    void revokeAllForUser_returnsCount() {
        when(sessionRepository.revokeAllByUserId(eq("alice"), any(Instant.class), eq("admin-revoke")))
                .thenReturn(3);

        int count = service.revokeAllForUser("alice", "admin-revoke");

        assertThat(count).isEqualTo(3);
        verify(sessionRepository).revokeAllByUserId(eq("alice"), any(Instant.class), eq("admin-revoke"));
    }

    // -----------------------------------------------------------------------
    // throttle lastUsedAt
    // -----------------------------------------------------------------------

    @Test
    void resolve_calledTwiceQuickly_updateLastUsedAtCalledOnceOnly() {
        IssuedToken issued = service.issue(mockAuth, null, Duration.ofHours(1));

        UserSession activeSession = new UserSession(
                issued.sessionId(),
                hashOf(issued.rawToken()),
                "alice",
                Instant.now(),
                issued.expiresAt(),
                Instant.now(),
                null,
                null,
                null,
                null);
        when(sessionRepository.findActiveByTokenHash(eq(hashOf(issued.rawToken())), any(Instant.class)))
                .thenReturn(Optional.of(activeSession));
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(mockUserDetails);

        // First resolve — should trigger updateLastUsedAt
        service.resolve(issued.rawToken());
        // Second resolve immediately — should NOT trigger updateLastUsedAt again
        service.resolve(issued.rawToken());

        verify(sessionRepository, times(1)).updateLastUsedAt(eq(issued.sessionId()), any(Instant.class));
    }

    // -----------------------------------------------------------------------
    // listActiveSessions()
    // -----------------------------------------------------------------------

    @Test
    void listActiveSessions_delegatesToRepository() {
        UserSession s1 = new UserSession(
                "id1",
                "hash1",
                "alice",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Instant.now(),
                null,
                null,
                null,
                null);
        when(sessionRepository.findAllActiveByUserId(eq("alice"), any(Instant.class)))
                .thenReturn(List.of(s1));

        List<UserSession> sessions = service.listActiveSessions("alice");

        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).id()).isEqualTo("id1");
    }

    // -----------------------------------------------------------------------
    // UserSession.isActive()
    // -----------------------------------------------------------------------

    @Test
    void userSession_isActive_trueWhenNotRevokedAndNotExpired() {
        UserSession session = new UserSession(
                "id",
                "hash",
                "alice",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Instant.now(),
                null,
                null,
                null,
                null);

        assertThat(session.isActive(Instant.now())).isTrue();
    }

    @Test
    void userSession_isActive_falseWhenRevoked() {
        UserSession session = new UserSession(
                "id",
                "hash",
                "alice",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Instant.now(),
                null,
                null,
                Instant.now(),
                "logout");

        assertThat(session.isActive(Instant.now())).isFalse();
    }

    @Test
    void userSession_isActive_falseWhenExpired() {
        UserSession session = new UserSession(
                "id",
                "hash",
                "alice",
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600),
                Instant.now(),
                null,
                null,
                null,
                null);

        assertThat(session.isActive(Instant.now())).isFalse();
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    /** Replicates the SHA-256 hex logic of OpaqueSessionTokenService. */
    private static String hashOf(String rawToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
