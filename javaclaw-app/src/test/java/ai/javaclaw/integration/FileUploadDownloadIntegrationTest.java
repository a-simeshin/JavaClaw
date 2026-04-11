package ai.javaclaw.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Integration test for file upload (POST /api/files/upload) and download
 * (GET /api/files/download/**) endpoints (roadmap 11.3).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FileUploadDownloadIntegrationTest extends IntegrationTestBase {

    private static final String UPLOAD_PATH = "upload-test/hello.txt";
    private static final String UPLOAD_CONTENT = "Hello from upload!";

    @Test
    @Order(1)
    void uploadFileCreatesVirtualFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", UPLOAD_CONTENT.getBytes());

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("path", UPLOAD_PATH)
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.path").value(UPLOAD_PATH))
                .andExpect(jsonPath("$.content").value(UPLOAD_CONTENT));
    }

    @Test
    @Order(2)
    void downloadReturnsFileContent() throws Exception {
        mockMvc.perform(get("/api/files/download/" + UPLOAD_PATH).with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(UPLOAD_CONTENT.getBytes()))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("hello.txt")));
    }

    @Test
    @Order(3)
    void uploadWithoutPathUsesOriginalFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "auto-named.md", "text/markdown", "# Auto".getBytes());

        mockMvc.perform(multipart("/api/files/upload").file(file).with(httpBasic("admin", "admin")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.path").value("auto-named.md"));
    }

    @Test
    @Order(4)
    void uploadOverwritesExistingFile() throws Exception {
        String updated = "Updated content";
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", updated.getBytes());

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("path", UPLOAD_PATH)
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value(updated));
    }

    @Test
    @Order(5)
    void downloadNonExistentFileReturns4xx() throws Exception {
        mockMvc.perform(get("/api/files/download/nonexistent/file.txt").with(httpBasic("admin", "admin")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(6)
    void uploadRequiresAuthentication() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "anon.txt", "text/plain", "anon".getBytes());

        mockMvc.perform(multipart("/api/files/upload").file(file).with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(7)
    void downloadRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/files/download/some/file.txt").with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(8)
    void userCannotDownloadOtherUsersFile() throws Exception {
        // admin uploaded UPLOAD_PATH in order 1 — user should not see it (falls back to global, not found → 500)
        mockMvc.perform(get("/api/files/download/" + UPLOAD_PATH).with(httpBasic("user", "user")))
                .andExpect(status().is4xxClientError());
    }
}
