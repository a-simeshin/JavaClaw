package ai.javaclaw.integration;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for DB-backed user management (Phase 7.1 + 7.2).
 *
 * <p>Does NOT extend IntegrationTestBase — needs explicit auth per request.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("contracttest")
class UserManagementIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Nested
    @DisplayName("DB-backed authentication")
    class DbAuthTests {

        @Test
        @DisplayName("Admin can authenticate with DB credentials")
        void adminAuthFromDb() throws Exception {
            mockMvc.perform(get("/api/me").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("admin"));
        }

        @Test
        @DisplayName("User can authenticate with DB credentials")
        void userAuthFromDb() throws Exception {
            mockMvc.perform(get("/api/me").with(httpBasic("user", "user")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("user"));
        }

        @Test
        @DisplayName("Invalid password returns 401")
        void invalidPassword() throws Exception {
            mockMvc.perform(get("/api/me").with(httpBasic("admin", "wrong"))).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("User CRUD — admin only")
    class UserCrudTests {

        @Test
        @DisplayName("GET /api/users lists active users (admin)")
        void listUsers_asAdmin() throws Exception {
            mockMvc.perform(get("/api/users").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                    .andExpect(jsonPath("$[0].username").exists())
                    .andExpect(jsonPath("$[0].id").exists());
        }

        @Test
        @DisplayName("GET /api/users returns 403 for USER role")
        void listUsers_asUser_forbidden() throws Exception {
            mockMvc.perform(get("/api/users").with(httpBasic("user", "user"))).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/users creates a new user")
        void createUser() throws Exception {
            mockMvc.perform(post("/api/users")
                            .with(httpBasic("admin", "admin"))
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
                            .with(httpBasic("admin", "admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"pass\",\"role\":\"ADMIN\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Newly created user can authenticate")
        void createdUserCanAuth() throws Exception {
            // Create
            mockMvc.perform(post("/api/users")
                            .with(httpBasic("admin", "admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"authtest\",\"password\":\"secret\",\"role\":\"USER\"}"))
                    .andExpect(status().isCreated());

            // Authenticate
            mockMvc.perform(get("/api/me").with(httpBasic("authtest", "secret")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("authtest"));
        }

        @Test
        @DisplayName("DELETE /api/users/{id} deactivates user")
        void deactivateUser() throws Exception {
            // Create a user to deactivate
            var result = mockMvc.perform(post("/api/users")
                            .with(httpBasic("admin", "admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"todelete\",\"password\":\"pass\",\"role\":\"USER\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            var body = result.getResponse().getContentAsString();
            var id = com.jayway.jsonpath.JsonPath.read(body, "$.id").toString();

            // Deactivate
            mockMvc.perform(delete("/api/users/" + id).with(httpBasic("admin", "admin")))
                    .andExpect(status().isNoContent());

            // Deactivated user cannot authenticate
            mockMvc.perform(get("/api/me").with(httpBasic("todelete", "pass"))).andExpect(status().isUnauthorized());
        }
    }
}
