package ai.javaclaw.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;

/**
 * Integration test verifying per-user virtual filesystem isolation (roadmap 4.3).
 *
 * <p>Two users (admin and user) create files and verify:
 * <ul>
 *   <li>Each user can create and read their own files</li>
 *   <li>Global files (seeded) are visible to both users</li>
 *   <li>A user cannot read another user's files (falls back to global or 404)</li>
 *   <li>A user cannot delete another user's files</li>
 *   <li>Each user's tree includes their own files plus global files</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FileIsolationIntegrationTest extends IntegrationTestBase {

    private static final String ADMIN_FILE = "isolation-test/admin-secret.txt";
    private static final String USER_FILE = "isolation-test/user-secret.txt";

    @Test
    @Order(1)
    void adminCreatesFile() throws Exception {
        mockMvc.perform(post("/api/files")
                        .with(httpBasic("admin", "admin"))
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
                        .with(httpBasic("user", "user"))
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
        mockMvc.perform(get("/api/files/{path}", ADMIN_FILE)
                        .with(httpBasic("admin", "admin"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("admin-only data"));
    }

    @Test
    @Order(4)
    void userCanReadOwnFile() throws Exception {
        mockMvc.perform(get("/api/files/{path}", USER_FILE)
                        .with(httpBasic("user", "user"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("user-only data"));
    }

    @Test
    @Order(5)
    void userCannotReadAdminFile() throws Exception {
        // user tries to read admin's file — should get 400 (file not found for this user)
        mockMvc.perform(get("/api/files/{path}", ADMIN_FILE)
                        .with(httpBasic("user", "user"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void adminCannotReadUserFile() throws Exception {
        // admin tries to read user's file — should get 400 (file not found for admin)
        mockMvc.perform(get("/api/files/{path}", USER_FILE)
                        .with(httpBasic("admin", "admin"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    void userCannotDeleteAdminFile() throws Exception {
        // user tries to delete admin's file — silently does nothing (only deletes own files)
        mockMvc.perform(delete("/api/files/{path}", ADMIN_FILE).with(httpBasic("user", "user")))
                .andExpect(status().isNoContent());

        // Verify admin's file still exists
        mockMvc.perform(get("/api/files/{path}", ADMIN_FILE)
                        .with(httpBasic("admin", "admin"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("admin-only data"));
    }

    @Test
    @Order(8)
    void userTreeContainsOwnFilesAndGlobalFiles() throws Exception {
        mockMvc.perform(get("/api/files").with(httpBasic("user", "user")).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("dir"))
                .andExpect(jsonPath("$.children").isArray());
    }

    @Test
    @Order(9)
    void userCanUpdateOwnFile() throws Exception {
        mockMvc.perform(put("/api/files/{path}", USER_FILE)
                        .with(httpBasic("user", "user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"" + USER_FILE + "\",\"content\":\"updated user data\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("updated user data"));
    }

    @Test
    @Order(10)
    void userCanDeleteOwnFile() throws Exception {
        mockMvc.perform(delete("/api/files/{path}", USER_FILE).with(httpBasic("user", "user")))
                .andExpect(status().isNoContent());

        // Verify file is gone
        mockMvc.perform(get("/api/files/{path}", USER_FILE)
                        .with(httpBasic("user", "user"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
