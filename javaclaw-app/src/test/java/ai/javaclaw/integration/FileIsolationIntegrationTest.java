package ai.javaclaw.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.javaclaw.integration.support.IntegrationTestAuthHelper;
import ai.javaclaw.persistence.api.AppUserQueryRepository;
import ai.javaclaw.users.AppUser;
import ai.javaclaw.users.AppUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test verifying per-user virtual filesystem isolation (roadmap 4.3).
 *
 * <p>Uses cookie-based session auth via {@link IntegrationTestAuthHelper}
 * — does NOT extend {@link IntegrationTestBase} because the default admin
 * MockMvc post-processor there would override per-request user switching.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("contracttest")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FileIsolationIntegrationTest {

    private static final String ADMIN_FILE = "isolation-test/admin-secret.txt";
    private static final String USER_FILE = "isolation-test/user-secret.txt";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    AppUserQueryRepository appUserQueryRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Cookie adminCookie;
    private Cookie userCookie;

    @BeforeEach
    void loginBothUsers() throws Exception {
        resetPassword("admin", "admin");
        resetPassword("user", "user");
        adminCookie = IntegrationTestAuthHelper.loginAndGetSessionCookie(mockMvc, objectMapper, "admin", "admin");
        userCookie = IntegrationTestAuthHelper.loginAndGetSessionCookie(mockMvc, objectMapper, "user", "user");
    }

    private void resetPassword(String username, String rawPassword) {
        AppUser u = appUserRepository.findByUsername(username).orElseThrow();
        appUserQueryRepository.updatePassword(u.id(), passwordEncoder.encode(rawPassword));
    }

    @Test
    @Order(1)
    void adminCreatesFile() throws Exception {
        mockMvc.perform(post("/api/files")
                        .cookie(adminCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"" + ADMIN_FILE + "\",\"content\":\"admin-only data\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.path").value(ADMIN_FILE))
                .andExpect(jsonPath("$.content").value("admin-only data"));
    }

    @Test
    @Order(2)
    void userCreatesFile() throws Exception {
        mockMvc.perform(post("/api/files")
                        .cookie(userCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"" + USER_FILE + "\",\"content\":\"user-only data\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.path").value(USER_FILE))
                .andExpect(jsonPath("$.content").value("user-only data"));
    }

    @Test
    @Order(3)
    void adminCanReadOwnFile() throws Exception {
        mockMvc.perform(get("/api/files/{path}", ADMIN_FILE).cookie(adminCookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("admin-only data"));
    }

    @Test
    @Order(4)
    void userCanReadOwnFile() throws Exception {
        mockMvc.perform(get("/api/files/{path}", USER_FILE).cookie(userCookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("user-only data"));
    }

    @Test
    @Order(5)
    void userCannotReadAdminFile() throws Exception {
        mockMvc.perform(get("/api/files/{path}", ADMIN_FILE).cookie(userCookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void adminCannotReadUserFile() throws Exception {
        mockMvc.perform(get("/api/files/{path}", USER_FILE).cookie(adminCookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    void userCannotDeleteAdminFile() throws Exception {
        mockMvc.perform(delete("/api/files/{path}", ADMIN_FILE)
                        .cookie(userCookie)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // Verify admin's file still exists
        mockMvc.perform(get("/api/files/{path}", ADMIN_FILE).cookie(adminCookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("admin-only data"));
    }

    @Test
    @Order(8)
    void userTreeContainsOwnFilesAndGlobalFiles() throws Exception {
        mockMvc.perform(get("/api/files").cookie(userCookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("dir"))
                .andExpect(jsonPath("$.children").isArray());
    }

    @Test
    @Order(9)
    void userCanUpdateOwnFile() throws Exception {
        mockMvc.perform(put("/api/files/{path}", USER_FILE)
                        .cookie(userCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"" + USER_FILE + "\",\"content\":\"updated user data\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("updated user data"));
    }

    @Test
    @Order(10)
    void userCanDeleteOwnFile() throws Exception {
        mockMvc.perform(delete("/api/files/{path}", USER_FILE)
                        .cookie(userCookie)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // Verify file is gone
        mockMvc.perform(get("/api/files/{path}", USER_FILE).cookie(userCookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
