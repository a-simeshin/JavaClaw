package ai.javaclaw.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for Spring Security authentication.
 *
 * <p><b>Currently disabled</b>: Spring Security is not yet wired into the application.
 * Enable these tests once security auto-configuration is added and the login/logout
 * endpoints are available.
 *
 * <p>TODO: Enable when Spring Security is configured (see react-spa-web-ui.md task 14).
 * Expected test scenarios:
 * <ul>
 *   <li>Unauthenticated request to /api/** returns 401</li>
 *   <li>POST /api/auth/login with valid credentials returns 200 + session cookie</li>
 *   <li>POST /api/auth/login with invalid credentials returns 401</li>
 *   <li>GET /api/auth/me with valid session returns user details</li>
 *   <li>POST /api/auth/logout invalidates session</li>
 *   <li>Admin-only endpoints require ROLE_ADMIN</li>
 * </ul>
 */
@Disabled("Spring Security is not yet wired — enable when auth endpoints are available (task 14)")
class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void unauthenticatedRequest_returns401() {
        // TODO: mockMvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithValidCredentials_returns200() {
        // TODO: mockMvc.perform(post("/api/auth/login")
        //         .contentType(MediaType.APPLICATION_JSON)
        //         .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
        //     .andExpect(status().isOk())
        //     .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    void loginWithInvalidCredentials_returns401() {
        // TODO: mockMvc.perform(post("/api/auth/login")
        //         .contentType(MediaType.APPLICATION_JSON)
        //         .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
        //     .andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_withValidSession_returnsUserDetails() {
        // TODO: perform login, then GET /api/auth/me with session cookie
    }

    @Test
    void logout_invalidatesSession() {
        // TODO: perform login, then POST /api/auth/logout, then verify session is invalidated
    }
}
