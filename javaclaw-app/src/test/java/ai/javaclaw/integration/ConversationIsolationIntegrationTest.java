package ai.javaclaw.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;

/**
 * Integration test verifying per-user conversation isolation (roadmap 4.2).
 *
 * <p>Two users (admin and user) create conversations and verify:
 * <ul>
 *   <li>Each user only sees their own conversations in the list</li>
 *   <li>A user cannot read messages from another user's conversation</li>
 *   <li>A user cannot delete another user's conversation</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConversationIsolationIntegrationTest extends IntegrationTestBase {

    private static final String ADMIN_CONV_ID = "isolation-admin-conv";
    private static final String USER_CONV_ID = "isolation-user-conv";

    @Test
    @Order(1)
    void adminCreatesConversation() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .with(httpBasic("admin", "admin"))
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
                        .with(httpBasic("user", "user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"User Chat\",\"id\":\"" + USER_CONV_ID + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_CONV_ID))
                .andExpect(jsonPath("$.title").value("User Chat"));
    }

    @Test
    @Order(3)
    void adminSeesOnlyOwnConversations() throws Exception {
        mockMvc.perform(get("/api/conversations")
                        .with(httpBasic("admin", "admin"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + ADMIN_CONV_ID + "')]")
                        .exists())
                .andExpect(
                        jsonPath("$.content[?(@.id == '" + USER_CONV_ID + "')]").doesNotExist());
    }

    @Test
    @Order(4)
    void userSeesOnlyOwnConversations() throws Exception {
        mockMvc.perform(get("/api/conversations")
                        .with(httpBasic("user", "user"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[?(@.id == '" + USER_CONV_ID + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.id == '" + ADMIN_CONV_ID + "')]")
                        .doesNotExist());
    }

    @Test
    @Order(5)
    void userCannotReadAdminMessages() throws Exception {
        mockMvc.perform(get("/api/conversations/{id}/messages", ADMIN_CONV_ID)
                        .with(httpBasic("user", "user"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @Order(6)
    void userCannotDeleteAdminConversation() throws Exception {
        mockMvc.perform(delete("/api/conversations/{id}", ADMIN_CONV_ID).with(httpBasic("user", "user")))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    void adminCanDeleteOwnConversation() throws Exception {
        mockMvc.perform(delete("/api/conversations/{id}", ADMIN_CONV_ID).with(httpBasic("admin", "admin")))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(8)
    void userCanDeleteOwnConversation() throws Exception {
        mockMvc.perform(delete("/api/conversations/{id}", USER_CONV_ID).with(httpBasic("user", "user")))
                .andExpect(status().isNoContent());
    }
}
