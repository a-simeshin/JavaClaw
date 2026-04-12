package ai.javaclaw.integration;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.javaclaw.integration.support.IntegrationTestAuthHelper;
import ai.javaclaw.persistence.api.AppUserQueryRepository;
import ai.javaclaw.users.AppUser;
import ai.javaclaw.users.AppUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for DB-backed user management (Phase 7.1 + 7.2).
 *
 * <p>Uses cookie-based session auth via {@link IntegrationTestAuthHelper}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("contracttest")
class UserManagementIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    AppUserQueryRepository appUserQueryRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void seedPasswords() {
        resetPassword("admin", "admin");
        resetPassword("user", "user");
    }

    private void resetPassword(String username, String rawPassword) {
        AppUser u = appUserRepository.findByUsername(username).orElseThrow();
        appUserQueryRepository.updatePassword(u.id(), passwordEncoder.encode(rawPassword));
    }

    private Cookie adminCookie() throws Exception {
        return IntegrationTestAuthHelper.adminCookie(mockMvc, objectMapper);
    }

    private Cookie loginCookie(String user, String pass) throws Exception {
        return IntegrationTestAuthHelper.loginAndGetSessionCookie(mockMvc, objectMapper, user, pass);
    }

    @Nested
    @DisplayName("DB-backed authentication")
    class DbAuthTests {

        @Test
        @DisplayName("Admin can authenticate with DB credentials")
        void adminAuthFromDb() throws Exception {
            Cookie c = adminCookie();
            mockMvc.perform(get("/api/auth/me").cookie(c))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("admin"));
        }

        @Test
        @DisplayName("User can authenticate with DB credentials")
        void userAuthFromDb() throws Exception {
            Cookie c = loginCookie("user", "user");
            mockMvc.perform(get("/api/auth/me").cookie(c))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("user"));
        }

        @Test
        @DisplayName("Invalid password returns 401")
        void invalidPassword() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of("username", "admin", "password", "wrong"));
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("User CRUD — admin only")
    class UserCrudTests {

        @Test
        @DisplayName("GET /api/users lists active users (admin)")
        void listUsers_asAdmin() throws Exception {
            mockMvc.perform(get("/api/users").cookie(adminCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                    .andExpect(jsonPath("$[0].username").exists())
                    .andExpect(jsonPath("$[0].id").exists());
        }

        @Test
        @DisplayName("GET /api/users returns 403 for USER role")
        void listUsers_asUser_forbidden() throws Exception {
            mockMvc.perform(get("/api/users").cookie(loginCookie("user", "user")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/users creates a new user")
        void createUser() throws Exception {
            mockMvc.perform(post("/api/users")
                            .cookie(adminCookie())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"newuser\",\"password\":\"pass123\",\"role\":\"USER\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("newuser"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.active").value(true));
        }

        @Test
        @DisplayName("POST /api/users with duplicate username returns 400")
        void createDuplicateUser() throws Exception {
            // admin already exists in seed data
            mockMvc.perform(post("/api/users")
                            .cookie(adminCookie())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"pass\",\"role\":\"ADMIN\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Newly created user can authenticate")
        void createdUserCanAuth() throws Exception {
            // Create
            mockMvc.perform(post("/api/users")
                            .cookie(adminCookie())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"authtest\",\"password\":\"secret\",\"role\":\"USER\"}"))
                    .andExpect(status().isCreated());

            // Authenticate via cookie login
            Cookie c = loginCookie("authtest", "secret");
            mockMvc.perform(get("/api/auth/me").cookie(c))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("authtest"));
        }

        @Test
        @DisplayName("DELETE /api/users/{id} deactivates user")
        void deactivateUser() throws Exception {
            Cookie admin = adminCookie();
            // Create a user to deactivate
            var result = mockMvc.perform(post("/api/users")
                            .cookie(admin)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"todelete\",\"password\":\"pass\",\"role\":\"USER\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            var body = result.getResponse().getContentAsString();
            var id = com.jayway.jsonpath.JsonPath.read(body, "$.id").toString();

            // Deactivate
            mockMvc.perform(delete("/api/users/" + id).cookie(admin).with(csrf()))
                    .andExpect(status().isNoContent());

            // Deactivated user cannot authenticate (login rejected)
            String loginBody = objectMapper.writeValueAsString(Map.of("username", "todelete", "password", "pass"));
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody))
                    .andExpect(status().isUnauthorized());
        }
    }
}
