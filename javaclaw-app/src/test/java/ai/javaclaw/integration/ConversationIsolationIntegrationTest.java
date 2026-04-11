package ai.javaclaw.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.javaclaw.integration.support.IntegrationTestAuthHelper;
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
 * Integration test verifying per-user conversation isolation (roadmap 4.2)
 * and admin conversation access (roadmap 15.5.5).
 *
 * <p>Uses cookie-based session auth via {@link IntegrationTestAuthHelper}
 * — does NOT extend {@link IntegrationTestBase} because the default admin
 * MockMvc post-processor there would override per-request user switching.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("contracttest")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConversationIsolationIntegrationTest {

    private static final String ADMIN_CONV_ID = "isolation-admin-conv";
    private static final String USER_CONV_ID = "isolation-user-conv";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AppUserRepository appUserRepository;

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
        appUserRepository.updatePassword(u.id(), passwordEncoder.encode(rawPassword));
    }

    @Test
    @Order(1)
    void adminCreatesConversation() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .cookie(adminCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Admin Chat\",\"id\":\"" + ADMIN_CONV_ID + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ADMIN_CONV_ID))
                .andExpect(jsonPath("$.title").value("Admin Chat"));
    }

    @Test
    @Order(2)
    void userCreatesConversation() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .cookie(userCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"User Chat\",\"id\":\"" + USER_CONV_ID + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_CONV_ID))
                .andExpect(jsonPath("$.title").value("User Chat"));
    }

    @Test
    @Order(3)
    void adminSeesAllConversationsIncludingOtherUsers() throws Exception {
        // Admin has CONVERSATION_ACCESS_ALL permission and can see all conversations
        mockMvc.perform(get("/api/conversations").cookie(adminCookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + ADMIN_CONV_ID + "')]")
                        .exists())
                .andExpect(
                        jsonPath("$.content[?(@.id == '" + USER_CONV_ID + "')]").exists());
    }

    @Test
    @Order(4)
    void userSeesOnlyOwnConversations() throws Exception {
        mockMvc.perform(get("/api/conversations").cookie(userCookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[?(@.id == '" + USER_CONV_ID + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.id == '" + ADMIN_CONV_ID + "')]")
                        .doesNotExist());
    }

    @Test
    @Order(5)
    void userCannotReadAdminMessages() throws Exception {
        // After @PreAuthorize("hasPermission(#id,'conversation','read')") — non-owned,
        // non-shared conversations without CONVERSATION_ACCESS_ALL return 403 Forbidden.
        mockMvc.perform(get("/api/conversations/{id}/messages", ADMIN_CONV_ID)
                        .cookie(userCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    void userCannotDeleteAdminConversation() throws Exception {
        // Same rationale: @PreAuthorize blocks non-owner deletes with 403.
        mockMvc.perform(delete("/api/conversations/{id}", ADMIN_CONV_ID)
                        .cookie(userCookie)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    void adminCanDeleteOwnConversation() throws Exception {
        mockMvc.perform(delete("/api/conversations/{id}", ADMIN_CONV_ID)
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(8)
    void userCanDeleteOwnConversation() throws Exception {
        mockMvc.perform(delete("/api/conversations/{id}", USER_CONV_ID)
                        .cookie(userCookie)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }
}
