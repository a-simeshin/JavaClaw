package ai.javaclaw.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.javaclaw.users.AppUser;
import ai.javaclaw.users.AppUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test for the new cookie/session-based auth module (Phase 15, task #16).
 *
 * <p>Boots the full application with the {@code contracttest} profile (Testcontainers
 * PostgreSQL + Flyway). Seeds a known Argon2 password hash for {@code admin}/{@code user}
 * because {@code V10__seed_default_users.sql} leaves {@code password_hash} as NULL.
 *
 * <p>Covers: login (success/failure), /me, logout, logout-all, sessions listing,
 * protected endpoint access via cookie, and absence of {@code WWW-Authenticate}
 * (the API must not trigger browser Basic-auth prompts).
 *
 * <p>Does NOT extend {@link IntegrationTestBase} — we need real anonymous requests and
 * real session cookies rather than the default {@code ROLE_ADMIN} MockMvc override.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("contracttest")
class AuthIntegrationTest {

    private static final String ADMIN_PASSWORD = "admin";
    private static final String USER_PASSWORD = "user";
    private static final String COOKIE_NAME = "JCLAW_SESSION";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ai.javaclaw.security.session.SessionTokenService sessionTokenService;

    /**
     * Seed real password hashes for admin/user so the {@link AuthenticationManager}
     * can successfully authenticate against the DB. V10 inserts NULL passwords.
     */
    @BeforeEach
    void seedPasswords() {
        resetPassword("admin", ADMIN_PASSWORD);
        resetPassword("user", USER_PASSWORD);
    }

    private void resetPassword(String username, String rawPassword) {
        AppUser u = appUserRepository.findByUsername(username).orElseThrow();
        appUserRepository.updatePassword(u.id(), passwordEncoder.encode(rawPassword));
    }

    // ── Login ──

    @Test
    @DisplayName("POST /api/auth/login with valid credentials returns 200 and sets JCLAW_SESSION cookie")
    void login_with_valid_credentials_returns_200_and_sets_cookie() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", "admin", "password", ADMIN_PASSWORD));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(cookie().exists(COOKIE_NAME))
                .andReturn();

        Cookie sessionCookie = result.getResponse().getCookie(COOKIE_NAME);
        assertThat(sessionCookie).isNotNull();
        assertThat(sessionCookie.getValue()).isNotBlank();
        assertThat(sessionCookie.isHttpOnly()).isTrue();
    }

    @Test
    @DisplayName("POST /api/auth/login with wrong password returns 401 and no WWW-Authenticate header")
    void login_with_invalid_credentials_returns_401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", "admin", "password", "wrong"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    // ── /me ──

    @Test
    @DisplayName("GET /api/auth/me without cookie returns 401 without WWW-Authenticate")
    void me_without_cookie_returns_401_without_WWW_Authenticate() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    @DisplayName("GET /api/auth/me with valid cookie returns user info")
    void me_with_valid_cookie_returns_user_info() throws Exception {
        Cookie sessionCookie = loginAndGetCookie("admin", ADMIN_PASSWORD);

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.authorities").isArray())
                .andExpect(jsonPath("$.roles").isArray());
    }

    // ── Logout ──

    @Test
    @DisplayName("POST /api/auth/logout clears cookie and revoked cookie cannot be reused")
    void logout_clears_cookie_and_subsequent_me_returns_401() throws Exception {
        Cookie sessionCookie = loginAndGetCookie("admin", ADMIN_PASSWORD);

        mockMvc.perform(post("/api/auth/logout").cookie(sessionCookie).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/logout-all revokes all sessions for the user")
    void logout_all_revokes_all_sessions_for_user() throws Exception {
        Cookie c1 = loginAndGetCookie("admin", ADMIN_PASSWORD);
        Cookie c2 = loginAndGetCookie("admin", ADMIN_PASSWORD);

        mockMvc.perform(post("/api/auth/logout-all").cookie(c1).with(csrf())).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").cookie(c1)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").cookie(c2)).andExpect(status().isUnauthorized());
    }

    // ── Sessions listing ──

    @Test
    @DisplayName("GET /api/auth/sessions lists active sessions for current user")
    void sessions_endpoint_lists_active_sessions() throws Exception {
        // Clean slate — other tests in this class may have left active sessions behind
        // because the Spring context and Testcontainers DB are shared across methods.
        appUserRepository
                .findByUsername("admin")
                .ifPresent(u -> sessionTokenService.revokeAllForUser(u.username(), "test-cleanup"));

        Cookie c1 = loginAndGetCookie("admin", ADMIN_PASSWORD);
        loginAndGetCookie("admin", ADMIN_PASSWORD); // second session

        mockMvc.perform(get("/api/auth/sessions").cookie(c1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ── Protected endpoints ──

    @Test
    @DisplayName("GET /api/conversations without cookie returns 401")
    void protected_endpoint_without_cookie_returns_401() throws Exception {
        mockMvc.perform(get("/api/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    @DisplayName("GET /api/conversations with valid session cookie returns 200")
    void protected_endpoint_with_valid_cookie_returns_200() throws Exception {
        Cookie sessionCookie = loginAndGetCookie("admin", ADMIN_PASSWORD);

        mockMvc.perform(get("/api/conversations").cookie(sessionCookie)).andExpect(status().isOk());
    }

    // ── Helpers ──

    private Cookie loginAndGetCookie(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        // SessionCookieWriter writes via setHeader("Set-Cookie", ...), overriding the raw addCookie
        // entry. Parse the raw Set-Cookie header to extract the token so the cookie we send back
        // in follow-up requests matches exactly what the browser would send.
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        if (setCookie == null) {
            throw new IllegalStateException("No Set-Cookie header returned from /api/auth/login");
        }
        String token = null;
        for (String part : setCookie.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(COOKIE_NAME + "=")) {
                token = trimmed.substring((COOKIE_NAME + "=").length());
                break;
            }
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Empty " + COOKIE_NAME + " token in Set-Cookie: " + setCookie);
        }
        Cookie cookie = new Cookie(COOKIE_NAME, token);
        cookie.setPath("/");
        return cookie;
    }
}
