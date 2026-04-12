package ai.javaclaw.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test for file upload (POST /api/files/upload) and download
 * (GET /api/files/download/**) endpoints (roadmap 11.3).
 *
 * <p>Uses cookie-based session auth via {@link IntegrationTestAuthHelper}
 * — does NOT extend {@link IntegrationTestBase} because per-user switching
 * is required for the user-isolation test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("contracttest")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FileUploadDownloadIntegrationTest {

    private static final String UPLOAD_PATH = "upload-test/hello.txt";
    private static final String UPLOAD_CONTENT = "Hello from upload!";

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
    void uploadFileCreatesVirtualFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", UPLOAD_CONTENT.getBytes());

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("path", UPLOAD_PATH)
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.path").value(UPLOAD_PATH))
                .andExpect(jsonPath("$.content").value(UPLOAD_CONTENT));
    }

    @Test
    @Order(2)
    void downloadReturnsFileContent() throws Exception {
        mockMvc.perform(get("/api/files/download/" + UPLOAD_PATH).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(content().bytes(UPLOAD_CONTENT.getBytes()))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("hello.txt")));
    }

    @Test
    @Order(3)
    void uploadWithoutPathUsesOriginalFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "auto-named.md", "text/markdown", "# Auto".getBytes());

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .cookie(adminCookie)
                        .with(csrf()))
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
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value(updated));
    }

    @Test
    @Order(5)
    void downloadNonExistentFileReturns4xx() throws Exception {
        mockMvc.perform(get("/api/files/download/nonexistent/file.txt").cookie(adminCookie))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(6)
    void uploadRequiresAuthentication() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "anon.txt", "text/plain", "anon".getBytes());

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .with(anonymous())
                        .with(csrf()))
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
        // admin uploaded UPLOAD_PATH in order 1 — user should not see it (falls back to global, not found → 4xx)
        mockMvc.perform(get("/api/files/download/" + UPLOAD_PATH).cookie(userCookie))
                .andExpect(status().is4xxClientError());
    }
}
