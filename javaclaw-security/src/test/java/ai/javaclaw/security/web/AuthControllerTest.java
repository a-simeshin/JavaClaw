package ai.javaclaw.security.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import ai.javaclaw.security.audit.AuthAuditService;
import ai.javaclaw.security.authn.AuthenticationService;
import ai.javaclaw.security.authn.LoginResult;
import ai.javaclaw.security.authn.UserInfo;
import ai.javaclaw.security.config.CookieProperties;
import ai.javaclaw.security.session.IssuedToken;
import ai.javaclaw.security.session.SessionCookieWriter;
import ai.javaclaw.security.session.SessionTokenService;
import ai.javaclaw.security.session.UserSession;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private SessionTokenService sessionTokenService;

    @Mock
    private SessionCookieWriter cookieWriter;

    @Mock
    private AuthAuditService authAuditService;

    private MockMvc mockMvc;

    private static final CookieProperties COOKIE_PROPS =
            new CookieProperties("JCLAW_SESSION", "/", "", false, "Lax", 86400);

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new AuthController(
                        authenticationService, sessionTokenService, cookieWriter, COOKIE_PROPS, authAuditService))
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
    }

    // Helper: set Authentication on request (simulates authenticated user)
    private static RequestPostProcessor authenticatedAs(String username, String... authorities) {
        return request -> {
            var auths = new java.util.ArrayList<SimpleGrantedAuthority>();
            for (String a : authorities) auths.add(new SimpleGrantedAuthority(a));
            var auth = UsernamePasswordAuthenticationToken.authenticated(
                    new org.springframework.security.core.userdetails.User(username, "", auths), null, auths);
            request.setUserPrincipal(auth);
            org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(auth);
            return request;
        };
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithUserInfo() throws Exception {
        var user = new UserInfo("u-1", "alice", null, List.of("USER"), List.of("ROLE_USER"));
        var token = new IssuedToken("raw-tok", "sess-1", Instant.parse("2099-01-01T00:00:00Z"));
        when(authenticationService.login(eq("alice"), eq("secret"), any()))
                .thenReturn(new LoginResult.Success(token, user));
        doNothing().when(cookieWriter).write(any(), any());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.user.id").value("u-1"))
                .andExpect(jsonPath("$.sessionExpiresAt").value("2099-01-01T00:00:00Z"));

        verify(cookieWriter).write(any(), eq("raw-tok"));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        when(authenticationService.login(any(), any(), any())).thenReturn(new LoginResult.Failed("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_secondFactorRequired_returns202() throws Exception {
        when(authenticationService.login(any(), any(), any()))
                .thenReturn(new LoginResult.SecondFactorRequired("chal-1", List.of("TOTP")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret\"}"))
                .andExpect(status().isAccepted());
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────────────

    @Test
    void logout_withCookie_revokesTokenAndClearsCookie() throws Exception {
        doNothing().when(sessionTokenService).revoke(any(), any());
        doNothing().when(cookieWriter).clear(any());

        mockMvc.perform(post("/api/auth/logout")
                        .with(authenticatedAs("alice", "ROLE_USER"))
                        .cookie(new jakarta.servlet.http.Cookie("JCLAW_SESSION", "raw-tok")))
                .andExpect(status().isNoContent());

        verify(sessionTokenService).revoke(eq("raw-tok"), eq("logout"));
        verify(cookieWriter).clear(any());
    }

    @Test
    void logout_withoutCookie_stillClearsCookie() throws Exception {
        doNothing().when(cookieWriter).clear(any());

        mockMvc.perform(post("/api/auth/logout").with(authenticatedAs("alice"))).andExpect(status().isNoContent());

        verify(sessionTokenService, never()).revoke(any(), any());
        verify(cookieWriter).clear(any());
    }

    // ── POST /api/auth/logout-all ─────────────────────────────────────────────────

    @Test
    void logoutAll_revokesAllSessionsForUser() throws Exception {
        when(sessionTokenService.revokeAllForUser(eq("alice"), eq("logout_all")))
                .thenReturn(3);
        doNothing().when(cookieWriter).clear(any());

        mockMvc.perform(post("/api/auth/logout-all").with(authenticatedAs("alice", "ROLE_USER")))
                .andExpect(status().isNoContent());

        verify(sessionTokenService).revokeAllForUser(eq("alice"), eq("logout_all"));
        verify(cookieWriter).clear(any());
    }

    // ── GET /api/auth/me ──────────────────────────────────────────────────────────

    @Test
    void me_authenticated_returns200WithUserInfo() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(authenticatedAs("alice", "ROLE_USER", "PERM_CHAT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.id").value("alice"));
    }

    @Test
    void me_unauthenticated_returns401() throws Exception {
        // No authentication set — controller checks authentication == null
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    // ── GET /api/auth/sessions ────────────────────────────────────────────────────

    @Test
    void listSessions_returnsActiveSessions() throws Exception {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var session = new UserSession(
                "sess-1", "hash", "alice", now, now.plusSeconds(3600), now, "127.0.0.1", "TestAgent", null, null);
        when(sessionTokenService.listActiveSessions("alice")).thenReturn(List.of(session));

        mockMvc.perform(get("/api/auth/sessions").with(authenticatedAs("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("sess-1"))
                .andExpect(jsonPath("$[0].remoteAddr").value("127.0.0.1"))
                .andExpect(jsonPath("$[0].userAgent").value("TestAgent"));
    }

    @Test
    void listSessions_emptyList_returns200WithEmptyArray() throws Exception {
        when(sessionTokenService.listActiveSessions("alice")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/auth/sessions").with(authenticatedAs("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── DELETE /api/auth/sessions/{id} ───────────────────────────────────────────

    @Test
    void revokeSession_ownedSession_revokesAndReturns204() throws Exception {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var session = new UserSession(
                "sess-1", "hash", "alice", now, now.plusSeconds(3600), now, "127.0.0.1", "TestAgent", null, null);
        when(sessionTokenService.listActiveSessions("alice")).thenReturn(List.of(session));
        doNothing().when(sessionTokenService).revokeById(any(), any());

        mockMvc.perform(delete("/api/auth/sessions/sess-1").with(authenticatedAs("alice")))
                .andExpect(status().isNoContent());

        verify(sessionTokenService).revokeById(eq("sess-1"), eq("revoked_by_user"));
    }

    @Test
    void revokeSession_notOwnedSession_doesNotRevokeAndReturns204() throws Exception {
        when(sessionTokenService.listActiveSessions("alice")).thenReturn(Collections.emptyList());

        mockMvc.perform(delete("/api/auth/sessions/sess-other").with(authenticatedAs("alice")))
                .andExpect(status().isNoContent());

        verify(sessionTokenService, never()).revokeById(any(), any());
    }
}
