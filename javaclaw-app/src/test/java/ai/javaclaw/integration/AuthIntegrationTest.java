package ai.javaclaw.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for Spring Security Basic Auth (Phase 4.1).
 *
 * <p>Does NOT extend IntegrationTestBase or import TestSecurityConfig
 * so that unauthenticated requests are truly anonymous.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("contracttest")
class AuthIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    // ── Unauthenticated access ──

    @Nested
    @DisplayName("Unauthenticated requests")
    class UnauthenticatedTests {

        @Test
        @DisplayName("GET /api/conversations without auth returns 401")
        void apiEndpoint_withoutAuth_returns401() throws Exception {
            mockMvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/me without auth returns 401")
        void meEndpoint_withoutAuth_returns401() throws Exception {
            mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/health is public (no auth needed)")
        void healthEndpoint_withoutAuth_returns200() throws Exception {
            mockMvc.perform(get("/api/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        @DisplayName("GET /actuator/health is public (no auth needed)")
        void actuatorHealth_withoutAuth_returns200() throws Exception {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }

    // ── Basic Auth with valid credentials ──

    @Nested
    @DisplayName("Valid credentials")
    class ValidCredentialsTests {

        @Test
        @DisplayName("GET /api/me with admin Basic Auth returns admin/ADMIN")
        void meEndpoint_withAdminBasicAuth_returnsAdmin() throws Exception {
            mockMvc.perform(get("/api/me").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("admin"))
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        @DisplayName("GET /api/me with user Basic Auth returns user/USER")
        void meEndpoint_withUserBasicAuth_returnsUser() throws Exception {
            mockMvc.perform(get("/api/me").with(httpBasic("user", "user")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("user"))
                    .andExpect(jsonPath("$.role").value("USER"));
        }

        @Test
        @DisplayName("GET /api/conversations with valid auth returns 200")
        void apiEndpoint_withValidAuth_returns200() throws Exception {
            mockMvc.perform(get("/api/conversations").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk());
        }
    }

    // ── Invalid credentials ──

    @Nested
    @DisplayName("Invalid credentials")
    class InvalidCredentialsTests {

        @Test
        @DisplayName("GET /api/me with wrong password returns 401")
        void meEndpoint_withWrongPassword_returns401() throws Exception {
            mockMvc.perform(get("/api/me").with(httpBasic("admin", "wrong"))).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/me with unknown user returns 401")
        void meEndpoint_withUnknownUser_returns401() throws Exception {
            mockMvc.perform(get("/api/me").with(httpBasic("nobody", "pass"))).andExpect(status().isUnauthorized());
        }
    }

    // ── Role-based access ──

    @Nested
    @DisplayName("Role-based access control")
    class RoleBasedTests {

        @Test
        @DisplayName("GET /api/skills with ADMIN role returns 200")
        void adminEndpoint_withAdminRole_returns200() throws Exception {
            mockMvc.perform(get("/api/skills").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /api/skills with USER role returns 403")
        void adminEndpoint_withUserRole_returns403() throws Exception {
            mockMvc.perform(get("/api/skills").with(httpBasic("user", "user"))).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/mcp-servers with USER role returns 403")
        void mcpServersEndpoint_withUserRole_returns403() throws Exception {
            mockMvc.perform(get("/api/mcp-servers").with(httpBasic("user", "user")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/mcp-servers with ADMIN role returns 200")
        void mcpServersEndpoint_withAdminRole_returns200() throws Exception {
            mockMvc.perform(get("/api/mcp-servers").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk());
        }
    }
}
